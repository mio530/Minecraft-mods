import { ChannelType, PermissionFlagsBits, PermissionsBitField } from 'discord.js';
import { afterEach, describe, expect, it } from 'vitest';
import { SqliteStore } from '../src/database/sqlite.js';
import { SetupService } from '../src/services/setup/setupService.js';
import { memStore, testConfig } from './helpers.js';

/** Minimal fake Discord channel supporting the surface SetupService uses. */
class FakeChannel {
  availableTags: { name: string }[] = [];
  permissionOverwrites: {
    cache: Map<string, { allow: PermissionsBitField; deny: PermissionsBitField }>;
    set: (ows: { id: string; allow: PermissionsBitField; deny: PermissionsBitField }[]) => Promise<unknown>;
  };
  constructor(
    public id: string,
    public name: string,
    public type: ChannelType,
    public parentId: string | null,
    overwrites: { id: string; allow: PermissionsBitField; deny: PermissionsBitField }[] = [],
    tags: { name: string }[] = [],
  ) {
    this.availableTags = tags;
    const cache = new Map(overwrites.map((o) => [o.id, { allow: o.allow, deny: o.deny }]));
    this.permissionOverwrites = {
      cache,
      set: async (ows) => {
        this.permissionOverwrites.cache = new Map(ows.map((o) => [o.id, { allow: o.allow, deny: o.deny }]));
        return this;
      },
    };
  }
  isThread() {
    return false;
  }
  setAvailableTags(tags: { name: string }[]) {
    this.availableTags = tags;
    return Promise.resolve(this);
  }
  setParent() {
    return Promise.resolve(this);
  }
}

function makeGuild(opts: { perms?: bigint[]; staff?: string[] } = {}) {
  const cache = new Map<string, FakeChannel>();
  let counter = 0;
  const perms = new PermissionsBitField(
    opts.perms ?? [PermissionFlagsBits.ManageChannels, PermissionFlagsBits.ManageRoles],
  );
  const guild = {
    id: 'guild-1',
    roles: { everyone: { id: 'everyone' } },
    members: { me: { id: 'bot-1', permissions: perms } },
    client: { user: { id: 'bot-1' } },
    channels: {
      cache,
      fetch: async () => cache,
      create: async (o: {
        name: string;
        type: ChannelType;
        parent?: string;
        permissionOverwrites?: { id: string; allow: PermissionsBitField; deny: PermissionsBitField }[];
        availableTags?: { name: string }[];
      }) => {
        const id = `chan-${++counter}`;
        const ch = new FakeChannel(
          id,
          o.name,
          o.type,
          o.parent ?? null,
          o.permissionOverwrites ?? [],
          o.availableTags ?? [],
        );
        cache.set(id, ch);
        return ch;
      },
    },
  };
  return { guild, cache };
}

describe('SetupService', () => {
  let store: SqliteStore;
  afterEach(() => store?.close());

  it('status on an empty guild reports everything missing', async () => {
    store = memStore();
    const svc = new SetupService(testConfig({ staffRoleIds: ['staff-1'] }), store);
    const { guild } = makeGuild();
    const report = await svc.run(guild as never, 'status');
    expect(report.ok).toBe(false);
    expect(report.category.status).toBe('missing');
    expect(report.channels.every((c) => c.status === 'missing')).toBe(true);
  });

  it('ensure creates category + 3 channels and persists ids', async () => {
    store = memStore();
    const svc = new SetupService(testConfig({ staffRoleIds: ['staff-1'] }), store);
    const { guild, cache } = makeGuild();

    const report = await svc.run(guild as never, 'ensure');
    expect(report.ok).toBe(true);
    expect(report.category.status).toBe('created');
    expect(report.channels.map((c) => c.status)).toEqual(['created', 'created', 'created']);

    // 1 category + 3 channels
    expect(cache.size).toBe(4);
    const settings = store.getGuildSettings('guild-1')!;
    expect(settings.categoryId).toBeTruthy();
    expect(settings.submissionChannelId).toBeTruthy();
    expect(settings.votingChannelId).toBeTruthy();
    expect(settings.logChannelId).toBeTruthy();

    // forum type + tags
    const forum = cache.get(settings.submissionChannelId!)!;
    expect(forum.type).toBe(ChannelType.GuildForum);
    expect(forum.availableTags.map((t) => t.name)).toEqual(['Gameplay', 'Events', 'PvP', 'Economy', 'Other']);

    // voting denies SendMessages for @everyone
    const voting = cache.get(settings.votingChannelId!)!;
    const everyoneDeny = voting.permissionOverwrites.cache.get('everyone')!.deny.bitfield;
    expect((everyoneDeny & PermissionFlagsBits.SendMessages) === PermissionFlagsBits.SendMessages).toBe(true);
    const everyoneAllow = voting.permissionOverwrites.cache.get('everyone')!.allow.bitfield;
    expect((everyoneAllow & PermissionFlagsBits.ViewChannel) === PermissionFlagsBits.ViewChannel).toBe(true);

    // log hides from @everyone and shows staff
    const log = cache.get(settings.logChannelId!)!;
    expect((log.permissionOverwrites.cache.get('everyone')!.deny.bitfield & PermissionFlagsBits.ViewChannel) !== 0n).toBe(true);
    expect(log.permissionOverwrites.cache.has('staff-1')).toBe(true);
  });

  it('is idempotent — a second ensure creates no duplicates', async () => {
    store = memStore();
    const svc = new SetupService(testConfig({ staffRoleIds: ['staff-1'] }), store);
    const { guild, cache } = makeGuild();
    await svc.run(guild as never, 'ensure');
    const sizeAfterFirst = cache.size;
    const report = await svc.run(guild as never, 'ensure');
    expect(cache.size).toBe(sizeAfterFirst); // no new channels
    expect(report.ok).toBe(true);
    expect(report.channels.every((c) => c.status === 'ok')).toBe(true);
  });

  it('recreates only missing channels (resumable setup)', async () => {
    store = memStore();
    const svc = new SetupService(testConfig({ staffRoleIds: ['staff-1'] }), store);
    const { guild, cache } = makeGuild();
    await svc.run(guild as never, 'ensure');
    const settings = store.getGuildSettings('guild-1')!;

    // Simulate someone deleting the voting channel.
    cache.delete(settings.votingChannelId!);

    const report = await svc.run(guild as never, 'ensure');
    const voting = report.channels.find((c) => c.key === 'voting')!;
    expect(voting.status).toBe('created');
    const others = report.channels.filter((c) => c.key !== 'voting');
    expect(others.every((c) => c.status === 'ok')).toBe(true);
  });

  it('blocks setup when the bot lacks Manage Channels', async () => {
    store = memStore();
    const svc = new SetupService(testConfig(), store);
    const { guild, cache } = makeGuild({ perms: [] });
    const report = await svc.run(guild as never, 'ensure');
    expect(report.ok).toBe(false);
    expect(report.missingPermissions).toContain('Manage Channels');
    expect(cache.size).toBe(0); // nothing created
  });
});
