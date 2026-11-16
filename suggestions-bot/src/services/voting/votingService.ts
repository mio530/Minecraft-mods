import type { SuggestionStore } from '../../database/index.js';
import type { Suggestion, VoteTotals } from '../../types/index.js';
import type { VoteAction } from '../../bot/components/customId.js';
import { globalLocks } from '../../utils/locks.js';

export interface VoteOutcome {
  suggestion: Suggestion;
  totals: VoteTotals;
  /** Short message to show the voter ephemerally. */
  feedback: string;
}

export class VoteClosedError extends Error {}
export class VoteNotFoundError extends Error {}

/**
 * Pure voting logic over the store. Enforces one active vote per user and
 * serializes concurrent votes per suggestion to prevent race conditions.
 */
export class VotingService {
  constructor(private store: SuggestionStore) {}

  async handle(
    suggestionId: string,
    userId: string,
    action: VoteAction,
  ): Promise<VoteOutcome> {
    return globalLocks.run(`vote:${suggestionId}`, async () => {
      const suggestion = this.store.getSuggestion(suggestionId);
      if (!suggestion) throw new VoteNotFoundError(suggestionId);
      if (suggestion.votingClosed) throw new VoteClosedError(suggestionId);

      const existing = this.store.getVote(suggestionId, userId);

      let feedback: string;
      if (action === 'clear') {
        if (!existing) {
          feedback = 'You have no active vote to clear.';
        } else {
          this.store.removeVote(suggestionId, userId);
          feedback = 'Your vote was removed.';
        }
      } else {
        const value = action === 'up' ? 1 : -1;
        if (existing && existing.value === value) {
          this.store.removeVote(suggestionId, userId);
          feedback = 'Vote toggled off.';
        } else {
          this.store.castVote(suggestionId, userId, value);
          feedback = existing
            ? `Vote changed to ${action === 'up' ? '👍' : '👎'}.`
            : `Vote recorded: ${action === 'up' ? '👍' : '👎'}.`;
        }
      }

      const updated = this.store.getSuggestion(suggestionId)!;
      return { suggestion: updated, totals: this.store.getTotals(suggestionId), feedback };
    });
  }
}
