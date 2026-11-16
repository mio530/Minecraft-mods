import type { AnyThreadChannel } from 'discord.js';
import { logger } from '../../utils/logger.js';
import { processForumThread } from '../flows.js';
import type { BotContext } from '../context.js';

export async function onThreadCreate(
  thread: AnyThreadChannel,
  ctx: BotContext,
): Promise<void> {
  if (thread.parentId !== ctx.channelId('submission')) return;
  try {
    await processForumThread(ctx, thread);
  } catch (err) {
    logger.error(`Failed to process forum thread ${thread.id}:`, String(err));
  }
}
