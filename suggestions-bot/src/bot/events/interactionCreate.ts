import { type Interaction, MessageFlags } from 'discord.js';
import { logger } from '../../utils/logger.js';
import { commands } from '../commands/index.js';
import { parseVoteId } from '../components/customId.js';
import type { BotContext } from '../context.js';
import {
  VoteClosedError,
  VoteNotFoundError,
} from '../../services/voting/votingService.js';

export async function onInteraction(
  interaction: Interaction,
  ctx: BotContext,
): Promise<void> {
  try {
    if (interaction.isButton()) {
      const parsed = parseVoteId(interaction.customId);
      if (!parsed) return;
      await interaction.deferReply({ flags: MessageFlags.Ephemeral });
      try {
        const outcome = await ctx.voting.handle(
          parsed.suggestionId,
          interaction.user.id,
          parsed.action,
        );
        await ctx.refreshVoting(outcome.suggestion);
        await interaction.editReply(outcome.feedback);
      } catch (err) {
        if (err instanceof VoteClosedError) {
          await interaction.editReply('🔒 This voting is closed.');
        } else if (err instanceof VoteNotFoundError) {
          await interaction.editReply('This suggestion no longer exists.');
        } else {
          logger.error('Vote handling failed:', String(err));
          await interaction.editReply('Something went wrong. Please try again.');
        }
      }
      return;
    }

    if (interaction.isChatInputCommand()) {
      const cmd = commands.find((c) => c.data.name === interaction.commandName);
      if (!cmd) return;
      await cmd.execute(interaction, ctx);
      return;
    }
  } catch (err) {
    logger.error('interactionCreate handler error:', String(err));
    if (interaction.isRepliable() && !interaction.replied && !interaction.deferred) {
      await interaction
        .reply({ content: 'Unexpected error.', flags: MessageFlags.Ephemeral })
        .catch(() => undefined);
    }
  }
}
