import {
  Client,
  type EmbedBuilder,
  type TextChannel,
} from 'discord.js';
import type { BotConfig } from '../config/index.js';
import type { SuggestionStore } from '../database/index.js';
import type { Suggestion } from '../types/index.js';
import { logger } from '../utils/logger.js';
import { globalLocks } from '../utils/locks.js';
import { AiService } from '../services/ai/index.js';
import { ModerationService } from '../services/moderation/moderationService.js';
import { SuggestionService } from '../services/suggestions/suggestionService.js';
import { VotingService } from '../services/voting/votingService.js';
import { buildVoteButtons, buildVotingEmbed } from './components/embeds.js';

/**
 * Central wiring: owns the services and all Discord message side effects so the
 * rest of the code stays decoupled from discord.js specifics.
 */
export class BotContext {
  readonly ai: AiService;
  readonly suggestions: SuggestionService;
  readonly voting: VotingService;
  readonly moderation: ModerationService;

  constructor(
    readonly client: Client,
    readonly config: BotConfig,
    readonly store: SuggestionStore,
  ) {
    this.ai = new AiService(config);
    this.suggestions = new SuggestionService(config, store, this.ai);
    this.voting = new VotingService(store);
    this.moderation = new ModerationService(config, store);
  }

  authorTag(userId: string): string {
    return `<@${userId}>`;
  }

  private async textChannel(id: string): Promise<TextChannel | null> {
    try {
      const ch = await this.client.channels.fetch(id);
      if (ch && ch.isTextBased() && 'send' in ch) return ch as TextChannel;
    } catch (err) {
      logger.error(`Failed to fetch channel ${id}:`, String(err));
    }
    return null;
  }

  async log(...embeds: EmbedBuilder[]): Promise<void> {
    const ch = await this.textChannel(this.config.channels.log);
    if (!ch) {
      logger.warn('Log channel unavailable; skipping log message');
      return;
    }
    await ch.send({ embeds }).catch((e) => logger.error('Log send failed:', String(e)));
  }

  /**
   * Create the voting message for a suggestion and persist its id. Idempotent
   * and locked per suggestion so concurrent triggers (ThreadCreate event +
   * startup recovery) can never post two voting messages for the same id.
   */
  async publishVoting(s: Suggestion): Promise<Suggestion> {
    return globalLocks.run(`publish:${s.id}`, async () => {
      const fresh = this.store.getSuggestion(s.id) ?? s;
      if (fresh.votingMessageId) return fresh; // already published
      const ch = await this.textChannel(this.config.channels.voting);
      if (!ch) throw new Error('Voting channel unavailable');

      const totals = this.store.getTotals(fresh.id);
      const msg = await ch.send({
        embeds: [buildVotingEmbed(fresh, totals, this.authorTag(fresh.authorId))],
        components: [buildVoteButtons(fresh.id, fresh.votingClosed === 1)],
      });
      this.store.setVotingMessage(fresh.id, ch.id, msg.id);
      return this.store.getSuggestion(fresh.id)!;
    });
  }

  /** Re-render the voting message after a vote or moderation change. */
  async refreshVoting(s: Suggestion): Promise<void> {
    if (!s.votingChannelId || !s.votingMessageId) return;
    const ch = await this.textChannel(s.votingChannelId);
    if (!ch) return;
    try {
      const msg = await ch.messages.fetch(s.votingMessageId);
      const totals = this.store.getTotals(s.id);
      await msg.edit({
        embeds: [buildVotingEmbed(s, totals, this.authorTag(s.authorId))],
        components: [buildVoteButtons(s.id, s.votingClosed === 1)],
      });
    } catch (err) {
      logger.warn(`Could not refresh voting message for ${s.id}:`, String(err));
    }
  }
}
