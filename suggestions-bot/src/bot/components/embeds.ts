import {
  ActionRowBuilder,
  ButtonBuilder,
  ButtonStyle,
  EmbedBuilder,
} from 'discord.js';
import type { Suggestion, VoteTotals } from '../../types/index.js';
import { BRAND, STATUS_META, VOTE_EMOJI } from '../constants.js';
import { buildVoteId } from './customId.js';

function forumLink(guildId: string, forumPostId: string): string {
  // Forum posts are threads; linking to the thread channel id works in-client.
  return `https://discord.com/channels/${guildId}/${forumPostId}`;
}

function progressBar(ratio: number, size = 12): string {
  const filled = Math.round((ratio / 100) * size);
  return '█'.repeat(filled) + '░'.repeat(Math.max(0, size - filled));
}

export function buildVotingEmbed(
  s: Suggestion,
  totals: VoteTotals,
  authorTag: string,
): EmbedBuilder {
  const meta = STATUS_META[s.status];
  const title = s.aiTitle ?? s.originalTitle;
  const summary = s.aiSummary ?? s.originalDescription;

  const embed = new EmbedBuilder()
    .setColor(meta.color)
    .setTitle(`${meta.emoji} ${s.id} • ${title}`.slice(0, 256))
    .setDescription(summary.slice(0, 4000))
    .addFields(
      {
        name: `${VOTE_EMOJI.up} Upvotes`,
        value: String(totals.upvotes),
        inline: true,
      },
      {
        name: `${VOTE_EMOJI.down} Downvotes`,
        value: String(totals.downvotes),
        inline: true,
      },
      { name: '🗳️ Total', value: String(totals.total), inline: true },
      {
        name: '📊 Positive',
        value: `${progressBar(totals.positiveRatio)} ${totals.positiveRatio}%`,
        inline: false,
      },
      { name: 'Status', value: `${meta.emoji} ${meta.label}`, inline: true },
      { name: 'Author', value: authorTag, inline: true },
      {
        name: 'Original',
        value: `[Forum post](${forumLink(s.guildId, s.forumPostId)})`,
        inline: true,
      },
    )
    .setFooter({ text: `${BRAND.footer} • ${s.id}` })
    .setTimestamp(s.createdAt);

  if (s.votingClosed) {
    embed.addFields({ name: '🔒 Voting', value: 'This voting is closed.', inline: false });
  } else if (s.votingClosesAt) {
    embed.addFields({
      name: '⏳ Closes',
      value: `<t:${Math.floor(s.votingClosesAt / 1000)}:R>`,
      inline: false,
    });
  }
  return embed;
}

export function buildVoteButtons(
  suggestionId: string,
  closed: boolean,
): ActionRowBuilder<ButtonBuilder> {
  const up = new ButtonBuilder()
    .setCustomId(buildVoteId('up', suggestionId))
    .setEmoji(VOTE_EMOJI.up)
    .setLabel('Upvote')
    .setStyle(ButtonStyle.Success)
    .setDisabled(closed);
  const down = new ButtonBuilder()
    .setCustomId(buildVoteId('down', suggestionId))
    .setEmoji(VOTE_EMOJI.down)
    .setLabel('Downvote')
    .setStyle(ButtonStyle.Danger)
    .setDisabled(closed);
  const clear = new ButtonBuilder()
    .setCustomId(buildVoteId('clear', suggestionId))
    .setEmoji('♻️')
    .setLabel('Clear vote')
    .setStyle(ButtonStyle.Secondary)
    .setDisabled(closed);
  return new ActionRowBuilder<ButtonBuilder>().addComponents(up, down, clear);
}

export function buildLogNewSuggestion(s: Suggestion, authorTag: string): EmbedBuilder {
  return new EmbedBuilder()
    .setColor(BRAND.color)
    .setTitle(`📥 New suggestion ${s.id}`)
    .setDescription((s.aiSummary ?? s.originalDescription).slice(0, 2000))
    .addFields(
      { name: 'Title', value: (s.aiTitle ?? s.originalTitle).slice(0, 256), inline: false },
      { name: 'Author', value: authorTag, inline: true },
      { name: 'AI', value: s.aiStatus, inline: true },
      {
        name: 'Original',
        value: `[Forum post](https://discord.com/channels/${s.guildId}/${s.forumPostId})`,
        inline: true,
      },
    )
    .setFooter({ text: BRAND.footer })
    .setTimestamp();
}

export function buildLogModeration(
  s: Suggestion,
  staffTag: string,
  action: string,
  reason: string | null,
): EmbedBuilder {
  const meta = STATUS_META[s.status];
  return new EmbedBuilder()
    .setColor(meta.color)
    .setTitle(`🛡️ ${s.id} • ${action}`)
    .addFields(
      { name: 'New status', value: `${meta.emoji} ${meta.label}`, inline: true },
      { name: 'Staff', value: staffTag, inline: true },
      { name: 'Reason', value: reason ? reason.slice(0, 1000) : '—', inline: false },
    )
    .setFooter({ text: BRAND.footer })
    .setTimestamp();
}

export function buildLogEvaluation(
  s: Suggestion,
  totals: VoteTotals,
  hints: string[],
): EmbedBuilder {
  return new EmbedBuilder()
    .setColor(BRAND.color)
    .setTitle(`📋 Voting ended • ${s.id}`)
    .setDescription((s.aiTitle ?? s.originalTitle).slice(0, 256))
    .addFields(
      { name: 'Upvotes', value: String(totals.upvotes), inline: true },
      { name: 'Downvotes', value: String(totals.downvotes), inline: true },
      { name: 'Total', value: String(totals.total), inline: true },
      { name: 'Positive', value: `${totals.positiveRatio}%`, inline: true },
      {
        name: 'Decision helper',
        value: hints.length ? hints.map((h) => `• ${h}`).join('\n') : '• No threshold met',
        inline: false,
      },
      {
        name: 'Note',
        value: 'This is only guidance — staff makes the final decision.',
        inline: false,
      },
    )
    .setFooter({ text: BRAND.footer })
    .setTimestamp();
}
