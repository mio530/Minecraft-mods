import {
  CategoryChannel,
  ChannelType,
  type ForumChannel,
  type Guild,
  type GuildBasedChannel,
  PermissionFlagsBits,
  PermissionsBitField,
  type GuildChannelCreateOptions,
} from 'discord.js';
import type { BotConfig } from '../../config/index.js';
import type { SuggestionStore } from '../../database/index.js';
import type { ChannelKind } from '../../types/index.js';
import { logger } from '../../utils/logger.js';

export type SetupMode = 'ensure' | 'repair' | 'status';

export interface StepResult {
  key: 'category' | ChannelKind;
  name: string;
  status: 'ok' | 'created' | 'repaired' | 'missing' | 'error';
  id?: string;
  detail?: string;
  permissionsOk?: boolean;
}

export interface SetupReport {
  ok: boolean;
  mode: SetupMode;
  category: StepResult;
  channels: StepResult[];
  missingPermissions: string[];
  errors: string[];
}

export const CATEGORY_NAME = '💡 SUGGESTIONS';

const CHANNEL_DEF: Record<
  ChannelKind,
  { display: string; base: string; type: ChannelType.GuildForum | ChannelType.GuildText }
> = {
  submission: {
    display: '💡・suggestion-submissions',
    base: 'suggestion-submissions',
    type: ChannelType.GuildForum,
  },
  voting: { display: '📊・suggestion-voting', base: 'suggestion-voting', type: ChannelType.GuildText },
  log: { display: '📋・suggestion-logs', base: 'suggestion-logs', type: ChannelType.GuildText },
};

export const FORUM_TAGS = ['Gameplay', 'Events', 'PvP', 'Economy', 'Other'];

interface DesiredOverwrite {
  id: string;
  allow: bigint;
  deny: bigint;
}

const F = PermissionFlagsBits;

function or(...bits: bigint[]): bigint {
  return bits.reduce((a, b) => a | b, 0n);
}

/**
 * Fully automatic, idempotent Discord setup: category + three channels with
 * minimal permission overwrites, forum tags, and persisted channel IDs.
 */
export class SetupService {
  constructor(
    private cfg: BotConfig,
    private store: SuggestionStore,
  ) {}

  private botId(guild: Guild): string {
    return guild.members.me?.id ?? guild.client.user!.id;
  }

  private desiredOverwrites(guild: Guild, kind: ChannelKind): DesiredOverwrite[] {
    const everyone = guild.roles.everyone.id;
    const bot = this.botId(guild);
    const staff = this.cfg.staffRoleIds;

    if (kind === 'submission') {
      return [
        {
          id: everyone,
          allow: or(F.ViewChannel, F.ReadMessageHistory, F.CreatePublicThreads, F.SendMessagesInThreads, F.AddReactions),
          deny: 0n,
        },
        {
          id: bot,
          allow: or(F.ViewChannel, F.ReadMessageHistory, F.SendMessagesInThreads, F.ManageThreads, F.ManageMessages, F.EmbedLinks),
          deny: 0n,
        },
      ];
    }
    if (kind === 'voting') {
      return [
        {
          id: everyone,
          allow: or(F.ViewChannel, F.ReadMessageHistory),
          deny: or(F.SendMessages, F.SendMessagesInThreads, F.CreatePublicThreads, F.CreatePrivateThreads),
        },
        {
          id: bot,
          allow: or(F.ViewChannel, F.ReadMessageHistory, F.SendMessages, F.EmbedLinks, F.ManageMessages),
          deny: 0n,
        },
      ];
    }
    // log (private)
    const list: DesiredOverwrite[] = [
      { id: everyone, allow: 0n, deny: F.ViewChannel },
      {
        id: bot,
        allow: or(F.ViewChannel, F.ReadMessageHistory, F.SendMessages, F.EmbedLinks),
        deny: 0n,
      },
    ];
    for (const roleId of staff) {
      list.push({ id: roleId, allow: or(F.ViewChannel, F.ReadMessageHistory), deny: 0n });
    }
    return list;
  }

  private toCreateOverwrites(d: DesiredOverwrite[]) {
    return d.map((o) => ({
      id: o.id,
      allow: new PermissionsBitField(o.allow),
      deny: new PermissionsBitField(o.deny),
    }));
  }

  private permissionsOk(channel: GuildBasedChannel, desired: DesiredOverwrite[]): boolean {
    if (channel.isThread()) return false;
    for (const d of desired) {
      const existing = channel.permissionOverwrites.cache.get(d.id);
      if (!existing) return false;
      const allow = existing.allow.bitfield;
      const deny = existing.deny.bitfield;
      if ((allow & d.allow) !== d.allow) return false;
      if ((deny & d.deny) !== d.deny) return false;
    }
    return true;
  }

  /** Check the bot's own guild permissions needed for setup. */
  checkBotPermissions(guild: Guild): string[] {
    const me = guild.members.me;
    const missing: string[] = [];
    if (!me) return ['Bot member not found in guild'];
    if (!me.permissions.has(F.ManageChannels)) missing.push('Manage Channels');
    if (!me.permissions.has(F.ManageRoles)) missing.push('Manage Roles (for permission overwrites)');
    return missing;
  }

  private findByName(
    guild: Guild,
    base: string,
    type: ChannelType,
    parentId: string | null,
  ): GuildBasedChannel | null {
    for (const ch of guild.channels.cache.values()) {
      if (ch.type !== type) continue;
      if (parentId && ch.parentId !== parentId) continue;
      if (ch.name === base || ch.name.toLowerCase().includes(base)) return ch;
    }
    return null;
  }

  private async ensureCategory(guild: Guild, mode: SetupMode): Promise<{ channel: CategoryChannel | null; step: StepResult }> {
    const saved = this.store.getGuildSettings(guild.id);
    // 1) stored id
    if (saved?.categoryId) {
      const existing = guild.channels.cache.get(saved.categoryId);
      if (existing && existing.type === ChannelType.GuildCategory) {
        return {
          channel: existing as CategoryChannel,
          step: { key: 'category', name: CATEGORY_NAME, status: 'ok', id: existing.id },
        };
      }
    }
    // 2) by name
    const byName = this.findByName(guild, 'suggestions', ChannelType.GuildCategory, null);
    if (byName && byName.type === ChannelType.GuildCategory) {
      this.store.saveGuildSettings(guild.id, { categoryId: byName.id });
      return {
        channel: byName as CategoryChannel,
        step: { key: 'category', name: CATEGORY_NAME, status: 'ok', id: byName.id },
      };
    }
    // 3) create (not in status mode)
    if (mode === 'status') {
      return { channel: null, step: { key: 'category', name: CATEGORY_NAME, status: 'missing' } };
    }
    try {
      const created = await guild.channels.create({
        name: CATEGORY_NAME,
        type: ChannelType.GuildCategory,
        reason: 'RyzeSMP Suggestions setup',
      });
      this.store.saveGuildSettings(guild.id, { categoryId: created.id });
      return { channel: created, step: { key: 'category', name: CATEGORY_NAME, status: 'created', id: created.id } };
    } catch (err) {
      return { channel: null, step: { key: 'category', name: CATEGORY_NAME, status: 'error', detail: String(err) } };
    }
  }

  private savedChannelId(guildId: string, kind: ChannelKind): string | null {
    const s = this.store.getGuildSettings(guildId);
    if (!s) return null;
    return kind === 'submission' ? s.submissionChannelId : kind === 'voting' ? s.votingChannelId : s.logChannelId;
  }

  private persistChannelId(guildId: string, kind: ChannelKind, id: string): void {
    const patch =
      kind === 'submission'
        ? { submissionChannelId: id }
        : kind === 'voting'
          ? { votingChannelId: id }
          : { logChannelId: id };
    this.store.saveGuildSettings(guildId, patch);
  }

  private async applyForumTags(forum: ForumChannel): Promise<void> {
    const existing = new Set(forum.availableTags.map((t) => t.name));
    const missing = FORUM_TAGS.filter((t) => !existing.has(t));
    if (missing.length === 0) return;
    const tags = [
      ...forum.availableTags.map((t) => ({ name: t.name, moderated: t.moderated, emoji: t.emoji })),
      ...missing.map((name) => ({ name })),
    ];
    await forum.setAvailableTags(tags as never);
  }

  private async ensureChannel(
    guild: Guild,
    category: CategoryChannel | null,
    kind: ChannelKind,
    mode: SetupMode,
  ): Promise<StepResult> {
    const def = CHANNEL_DEF[kind];
    const desired = this.desiredOverwrites(guild, kind);

    const locate = (): GuildBasedChannel | null => {
      const savedId = this.savedChannelId(guild.id, kind);
      if (savedId) {
        const ch = guild.channels.cache.get(savedId);
        if (ch) return ch;
      }
      return this.findByName(guild, def.base, def.type, category?.id ?? null);
    };

    let channel = locate();

    // Wrong type guard — never delete user data; report for manual action.
    if (channel && channel.type !== def.type) {
      return {
        key: kind,
        name: def.display,
        status: 'error',
        id: channel.id,
        detail: `A channel named like "${def.base}" exists but is the wrong type; rename/remove it manually.`,
      };
    }

    if (!channel) {
      if (mode === 'status') return { key: kind, name: def.display, status: 'missing' };
      try {
        const options = {
          name: def.display,
          type: def.type,
          parent: category?.id,
          permissionOverwrites: this.toCreateOverwrites(desired),
          reason: 'RyzeSMP Suggestions setup',
          ...(def.type === ChannelType.GuildForum
            ? { availableTags: FORUM_TAGS.map((name) => ({ name })) }
            : {}),
        } as GuildChannelCreateOptions;
        const created = await guild.channels.create(options);
        this.persistChannelId(guild.id, kind, created.id);
        return { key: kind, name: def.display, status: 'created', id: created.id, permissionsOk: true };
      } catch (err) {
        return { key: kind, name: def.display, status: 'error', detail: String(err) };
      }
    }

    // Channel exists — persist its id (in case it was only found by name).
    this.persistChannelId(guild.id, kind, channel.id);
    const permsOk = this.permissionsOk(channel, desired);

    if (mode === 'repair') {
      try {
        if (channel.parentId !== (category?.id ?? null) && category && 'setParent' in channel) {
          await channel.setParent(category.id, { lockPermissions: false }).catch(() => undefined);
        }
        await channel.permissionOverwrites.set(this.toCreateOverwrites(desired), 'RyzeSMP Suggestions repair');
        if (def.type === ChannelType.GuildForum) {
          await this.applyForumTags(channel as ForumChannel);
        }
        return { key: kind, name: def.display, status: 'repaired', id: channel.id, permissionsOk: true };
      } catch (err) {
        return { key: kind, name: def.display, status: 'error', id: channel.id, detail: String(err) };
      }
    }

    return {
      key: kind,
      name: def.display,
      status: 'ok',
      id: channel.id,
      permissionsOk: permsOk,
      detail: permsOk ? undefined : 'Permissions differ from recommended — run /suggestion setup-repair.',
    };
  }

  /** Run setup/repair/status. Idempotent and safe to re-run (resumable). */
  async run(guild: Guild, mode: SetupMode): Promise<SetupReport> {
    await guild.channels.fetch().catch(() => undefined);

    const missingPermissions = mode === 'status' ? [] : this.checkBotPermissions(guild);
    const errors: string[] = [];

    if (missingPermissions.length > 0 && mode !== 'status') {
      return {
        ok: false,
        mode,
        category: { key: 'category', name: CATEGORY_NAME, status: 'error', detail: 'Missing bot permissions' },
        channels: (['submission', 'voting', 'log'] as ChannelKind[]).map((k) => ({
          key: k,
          name: CHANNEL_DEF[k].display,
          status: 'error' as const,
          detail: 'Blocked: missing bot permissions',
        })),
        missingPermissions,
        errors: ['Bot is missing required permissions: ' + missingPermissions.join(', ')],
      };
    }

    const { channel: category, step: categoryStep } = await this.ensureCategory(guild, mode);
    if (categoryStep.status === 'error') errors.push(`Category: ${categoryStep.detail}`);

    const channels: StepResult[] = [];
    for (const kind of ['submission', 'voting', 'log'] as ChannelKind[]) {
      const res = await this.ensureChannel(guild, category, kind, mode);
      if (res.status === 'error') errors.push(`${res.name}: ${res.detail}`);
      channels.push(res);
    }

    const ok =
      categoryStep.status !== 'error' &&
      channels.every((c) =>
        mode === 'status'
          ? c.status === 'ok' && c.permissionsOk !== false
          : c.status !== 'error' && c.status !== 'missing',
      );

    if (mode !== 'status') {
      logger.info(`Setup (${mode}) for guild ${guild.id}: ${ok ? 'OK' : 'incomplete'}`);
    }

    return { ok, mode, category: categoryStep, channels, missingPermissions, errors };
  }
}
