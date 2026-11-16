import {
  ChatInputCommandInteraction,
  EmbedBuilder,
  MessageFlags,
  SlashCommandBuilder,
  type SlashCommandSubcommandBuilder,
} from 'discord.js';
import { BRAND, STATUS_META } from '../constants.js';
import type { BotContext } from '../context.js';
import { isStaff, memberFrom } from '../permissions.js';
import { applyModeration } from '../flows.js';
import type { ModerationAction } from '../../services/moderation/moderationService.js';
import { SuggestionNotFoundError } from '../../services/moderation/moderationService.js';

export const data = new SlashCommandBuilder()
  .setName('suggestion')
  .setDescription('RyzeSMP suggestion system')
  .addSubcommand((s) => s.setName('setup').setDescription('Show setup & channel configuration'))
  .addSubcommand((s) => s.setName('config').setDescription('Show the current configuration'))
  .addSubcommand((s) => s.setName('stats').setDescription('Show suggestion statistics'))
  .addSubcommand((s) =>
    s
      .setName('leaderboard')
      .setDescription('Show the most popular suggestions')
      .addIntegerOption((o) =>
        o.setName('limit').setDescription('How many (default 10)').setMinValue(1).setMaxValue(25),
      ),
  )
  .addSubcommand((s) =>
    s
      .setName('view')
      .setDescription('View a specific suggestion')
      .addStringOption((o) => o.setName('id').setDescription('e.g. RS-001').setRequired(true)),
  )
  .addSubcommand((s) =>
    addId(s.setName('approve').setDescription('Approve a suggestion (staff)')),
  )
  .addSubcommand((s) => addId(s.setName('reject').setDescription('Reject a suggestion (staff)')))
  .addSubcommand((s) =>
    addId(s.setName('implement').setDescription('Mark a suggestion implemented (staff)')),
  )
  .addSubcommand((s) => addId(s.setName('close').setDescription('Close a voting (staff)')))
  .addSubcommand((s) => addId(s.setName('reopen').setDescription('Reopen a voting (staff)')));

function addId(sub: SlashCommandSubcommandBuilder): SlashCommandSubcommandBuilder {
  return sub
    .addStringOption((o) =>
      o.setName('id').setDescription('Suggestion id, e.g. RS-001').setRequired(true),
    )
    .addStringOption((o) => o.setName('reason').setDescription('Reason (optional)'));
}

const MOD_ACTIONS = new Set(['approve', 'reject', 'implement', 'close', 'reopen']);

export async function execute(
  interaction: ChatInputCommandInteraction,
  ctx: BotContext,
): Promise<void> {
  const sub = interaction.options.getSubcommand();

  if (sub === 'setup' || sub === 'config') {
    if (!isStaff(ctx.config, memberFrom(interaction))) {
      await deny(interaction);
      return;
    }
    await interaction.reply({
      embeds: [sub === 'setup' ? setupEmbed(ctx) : configEmbed(ctx)],
      flags: MessageFlags.Ephemeral,
    });
    return;
  }

  if (sub === 'stats') {
    await interaction.reply({ embeds: [statsEmbed(ctx)], flags: MessageFlags.Ephemeral });
    return;
  }

  if (sub === 'leaderboard') {
    const limit = interaction.options.getInteger('limit') ?? 10;
    await interaction.reply({ embeds: [leaderboardEmbed(ctx, limit)] });
    return;
  }

  if (sub === 'view') {
    const id = interaction.options.getString('id', true).toUpperCase();
    const s = ctx.store.getSuggestion(id);
    if (!s) {
      await interaction.reply({ content: `No suggestion \`${id}\`.`, flags: MessageFlags.Ephemeral });
      return;
    }
    await interaction.reply({ embeds: [viewEmbed(ctx, id)], flags: MessageFlags.Ephemeral });
    return;
  }

  if (MOD_ACTIONS.has(sub)) {
    if (!isStaff(ctx.config, memberFrom(interaction))) {
      await deny(interaction);
      return;
    }
    const id = interaction.options.getString('id', true).toUpperCase();
    const reason = interaction.options.getString('reason');
    await interaction.deferReply({ flags: MessageFlags.Ephemeral });
    try {
      const updated = await applyModeration(
        ctx,
        id,
        interaction.user.id,
        sub as ModerationAction,
        reason,
      );
      const meta = STATUS_META[updated.status];
      await interaction.editReply(
        `${meta.emoji} ${updated.id} → **${meta.label}**${reason ? ` (${reason})` : ''}`,
      );
    } catch (err) {
      if (err instanceof SuggestionNotFoundError) {
        await interaction.editReply(`No suggestion \`${id}\`.`);
      } else {
        await interaction.editReply(`Failed: ${String(err)}`);
      }
    }
    return;
  }
}

async function deny(interaction: ChatInputCommandInteraction): Promise<void> {
  await interaction.reply({
    content: '⛔ You do not have permission to use this.',
    flags: MessageFlags.Ephemeral,
  });
}

function setupEmbed(ctx: BotContext): EmbedBuilder {
  const c = ctx.config.channels;
  return new EmbedBuilder()
    .setColor(BRAND.color)
    .setTitle('⚙️ RyzeSMP Suggestions — Setup')
    .setDescription(
      [
        '**Channel A — Submissions (forum):** members post suggestions here.',
        '**Channel B — Voting (text):** the bot posts voting messages here.',
        '**Channel C — Logs (staff):** new suggestions, results and decisions.',
      ].join('\n'),
    )
    .addFields(
      { name: 'Submission channel', value: `<#${c.submission}>`, inline: false },
      { name: 'Voting channel', value: `<#${c.voting}>`, inline: false },
      { name: 'Log channel', value: `<#${c.log}>`, inline: false },
      {
        name: 'Required bot permissions',
        value:
          'View Channels, Send Messages, Embed Links, Read Message History in all three channels.',
        inline: false,
      },
    )
    .setFooter({ text: BRAND.footer });
}

function configEmbed(ctx: BotContext): EmbedBuilder {
  const v = ctx.config.voting;
  return new EmbedBuilder()
    .setColor(BRAND.color)
    .setTitle('🧩 Current configuration')
    .addFields(
      { name: 'AI provider', value: `${ctx.ai.providerName} (${ctx.config.ai.model})`, inline: true },
      { name: 'Staff roles', value: String(ctx.config.staffRoleIds.length || 'ManageGuild'), inline: true },
      { name: 'Voting duration', value: `${v.durationDays} day(s)`, inline: true },
      { name: 'Minimum votes', value: String(v.minimumVotes), inline: true },
      { name: 'Minimum upvotes', value: String(v.minimumUpvotes), inline: true },
      { name: 'Min positive ratio', value: `${v.minimumPositiveRatio}%`, inline: true },
      { name: 'Evaluation interval', value: `${v.evaluationIntervalMinutes} min`, inline: true },
    )
    .setFooter({ text: `${BRAND.footer} • secrets are never shown` });
}

function statsEmbed(ctx: BotContext): EmbedBuilder {
  const st = ctx.store.stats();
  return new EmbedBuilder()
    .setColor(BRAND.color)
    .setTitle('📈 Suggestion statistics')
    .addFields(
      { name: 'Total', value: String(st.total), inline: true },
      { name: '🟡 Pending', value: String(st.pending), inline: true },
      { name: '🟢 Approved', value: String(st.approved), inline: true },
      { name: '🔴 Rejected', value: String(st.rejected), inline: true },
      { name: '🔵 Implemented', value: String(st.implemented), inline: true },
      { name: '⚫ Closed', value: String(st.closed), inline: true },
    )
    .setFooter({ text: BRAND.footer });
}

function leaderboardEmbed(ctx: BotContext, limit: number): EmbedBuilder {
  const rows = ctx.store.leaderboard(limit);
  const body =
    rows.length === 0
      ? '_No suggestions yet._'
      : rows
          .map((r, i) => {
            const s = r.suggestion;
            const meta = STATUS_META[s.status];
            return `**${i + 1}.** ${meta.emoji} \`${s.id}\` ${
              s.aiTitle ?? s.originalTitle
            } — 👍 ${s.upvotes} / 👎 ${s.downvotes} (score ${r.score})`;
          })
          .join('\n');
  return new EmbedBuilder()
    .setColor(BRAND.color)
    .setTitle('🏆 Suggestion leaderboard')
    .setDescription(body.slice(0, 4000))
    .setFooter({ text: BRAND.footer });
}

function viewEmbed(ctx: BotContext, id: string): EmbedBuilder {
  const s = ctx.store.getSuggestion(id)!;
  const totals = ctx.store.getTotals(id);
  const meta = STATUS_META[s.status];
  const logs = ctx.store.listModLogs(id);
  const history =
    logs.length === 0
      ? '—'
      : logs
          .map((l) => `<t:${Math.floor(l.createdAt / 1000)}:d> ${l.action} by <@${l.staffId}>`)
          .join('\n')
          .slice(0, 1000);
  return new EmbedBuilder()
    .setColor(meta.color)
    .setTitle(`${meta.emoji} ${s.id} • ${s.aiTitle ?? s.originalTitle}`.slice(0, 256))
    .setDescription((s.aiSummary ?? s.originalDescription).slice(0, 2000))
    .addFields(
      { name: 'Status', value: `${meta.emoji} ${meta.label}`, inline: true },
      { name: 'Author', value: `<@${s.authorId}>`, inline: true },
      { name: 'AI', value: s.aiStatus, inline: true },
      { name: '👍', value: String(totals.upvotes), inline: true },
      { name: '👎', value: String(totals.downvotes), inline: true },
      { name: 'Positive', value: `${totals.positiveRatio}%`, inline: true },
      {
        name: 'Original',
        value: `[Forum post](https://discord.com/channels/${s.guildId}/${s.forumPostId})`,
        inline: false,
      },
      { name: 'Moderation history', value: history, inline: false },
    )
    .setFooter({ text: BRAND.footer })
    .setTimestamp(s.createdAt);
}
