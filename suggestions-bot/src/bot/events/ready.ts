import { ChannelType, type Client } from 'discord.js';
import { logger } from '../../utils/logger.js';
import { processForumThread } from '../flows.js';
import { commandJson } from '../commands/index.js';
import type { BotContext } from '../context.js';

/**
 * Startup recovery: register slash commands, catch forum posts created while
 * the bot was offline, and ensure every suggestion has its voting message.
 * Persistent buttons keep working thanks to stable custom IDs.
 */
export async function onReady(client: Client<true>, ctx: BotContext): Promise<void> {
  logger.info(`Logged in as ${client.user.tag} (AI provider: ${ctx.ai.providerName})`);

  // Register guild slash commands automatically so /suggestion setup is usable
  // on first start without a manual deploy step.
  try {
    const guild = await client.guilds.fetch(ctx.config.discord.guildId);
    await guild.commands.set(commandJson());
    logger.info('Slash commands registered for guild.');
  } catch (err) {
    logger.error('Automatic command registration failed (use npm run deploy-commands):', String(err));
  }

  const submissionId = ctx.channelId('submission');
  if (!submissionId) {
    logger.info('No submission channel configured yet — run /suggestion setup. Skipping recovery.');
    return;
  }
  try {
    const channel = await client.channels.fetch(submissionId);
    if (channel && channel.type === ChannelType.GuildForum) {
      const active = await channel.threads.fetchActive().catch(() => null);
      const archived = await channel.threads.fetchArchived({ limit: 50 }).catch(() => null);
      const threads = [
        ...(active?.threads.values() ?? []),
        ...(archived?.threads.values() ?? []),
      ];
      let recovered = 0;
      for (const thread of threads) {
        if (ctx.store.getByForumPost(thread.id)) continue;
        await processForumThread(ctx, thread);
        recovered++;
      }
      if (recovered > 0) logger.info(`Recovered ${recovered} missed forum post(s) on startup`);
    } else {
      logger.warn('Submission channel is not a forum channel — forum detection disabled.');
    }
  } catch (err) {
    logger.error('Startup recovery failed:', String(err));
  }

  // Ensure pending suggestions have a voting message (e.g. if a prior run crashed
  // after creating the record but before posting).
  for (const s of ctx.store.listByStatus('pending')) {
    if (!s.votingMessageId) {
      await ctx.publishVoting(s).catch((e) => logger.error('publishVoting (recovery):', String(e)));
    }
  }
}
