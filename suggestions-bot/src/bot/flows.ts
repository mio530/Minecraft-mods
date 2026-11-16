import { EmbedBuilder, type AnyThreadChannel } from 'discord.js';
import type { Suggestion } from '../types/index.js';
import { logger } from '../utils/logger.js';
import { truncate } from '../utils/text.js';
import { BRAND } from './constants.js';
import { buildLogModeration, buildLogNewSuggestion } from './components/embeds.js';
import type { BotContext } from './context.js';
import type { ModerationAction } from '../services/moderation/moderationService.js';
import { ACTION_LABEL } from '../services/moderation/moderationService.js';

/**
 * Full pipeline for a new forum submission: read content, create suggestion
 * (with AI summary + dedupe), publish the voting message, and log it.
 * Idempotent — safe to call twice for the same thread.
 */
export async function processForumThread(
  ctx: BotContext,
  thread: AnyThreadChannel,
): Promise<Suggestion | null> {
  if (thread.parentId !== ctx.channelId('submission')) return null;

  let description = '';
  try {
    const starter = await thread.fetchStarterMessage().catch(() => null);
    description = starter?.content ?? '';
  } catch (err) {
    logger.warn(`Could not fetch starter message for thread ${thread.id}:`, String(err));
  }

  const result = await ctx.suggestions.createFromForumPost({
    guildId: thread.guildId,
    forumPostId: thread.id,
    authorId: thread.ownerId ?? ctx.client.user!.id,
    title: thread.name,
    description,
  });

  if (!result.created) {
    // Already processed — make sure it has a voting message.
    const s = await ctx.publishVoting(result.suggestion).catch((e) => {
      logger.error('publishVoting failed (existing):', String(e));
      return result.suggestion;
    });
    return s;
  }

  const published = await ctx.publishVoting(result.suggestion);

  await ctx.log(buildLogNewSuggestion(published, ctx.authorTag(published.authorId)));

  if (result.duplicates.length > 0) {
    const lines = result.duplicates
      .slice(0, 5)
      .map(
        (d) =>
          `• ${d.suggestion.id} — ${truncate(d.suggestion.aiTitle ?? d.suggestion.originalTitle, 60)} (${Math.round(
            d.similarity * 100,
          )}%)`,
      );
    const embed = new EmbedBuilder()
      .setColor(0xf1c40f)
      .setTitle(`♻️ Possible duplicate of ${published.id}`)
      .setDescription(
        `This suggestion looks similar to existing ones:\n${lines.join('\n')}`,
      )
      .setFooter({ text: BRAND.footer })
      .setTimestamp();
    await ctx.log(embed);
  }

  logger.info(`Published suggestion ${published.id} from thread ${thread.id}`);
  return published;
}

/**
 * Apply a moderation action and reflect it in the voting message + log channel.
 */
export async function applyModeration(
  ctx: BotContext,
  suggestionId: string,
  staffId: string,
  action: ModerationAction,
  reason: string | null,
): Promise<Suggestion> {
  const updated = ctx.moderation.apply(suggestionId, staffId, action, reason);
  await ctx.refreshVoting(updated);
  await ctx.log(
    buildLogModeration(updated, ctx.authorTag(staffId), ACTION_LABEL[action], reason),
  );
  logger.info(`Moderation ${action} on ${suggestionId} by ${staffId}`);
  return updated;
}
