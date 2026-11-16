import type { BotConfig } from '../../config/index.js';
import type { SuggestionStore } from '../../database/index.js';
import type { Suggestion, SuggestionStatus } from '../../types/index.js';

export type ModerationAction =
  | 'approve'
  | 'reject'
  | 'implement'
  | 'close'
  | 'reopen'
  | 'end';

const ACTION_STATUS: Partial<Record<ModerationAction, SuggestionStatus>> = {
  approve: 'approved',
  reject: 'rejected',
  implement: 'implemented',
  close: 'closed',
  reopen: 'pending',
};

export const ACTION_LABEL: Record<ModerationAction, string> = {
  approve: 'Approved',
  reject: 'Rejected',
  implement: 'Marked implemented',
  close: 'Closed',
  reopen: 'Reopened',
  end: 'Voting ended',
};

export class SuggestionNotFoundError extends Error {}

export class ModerationService {
  constructor(
    private cfg: BotConfig,
    private store: SuggestionStore,
  ) {}

  /**
   * Apply a moderation action. Records who/when and updates status/voting flags.
   * Returns the updated suggestion.
   */
  apply(
    id: string,
    staffId: string,
    action: ModerationAction,
    reason: string | null,
  ): Suggestion {
    const current = this.store.getSuggestion(id);
    if (!current) throw new SuggestionNotFoundError(id);

    const newStatus = ACTION_STATUS[action];
    if (newStatus) {
      this.store.updateStatus(id, newStatus, staffId, reason);
    }

    if (action === 'close' || action === 'end' || action === 'approve' || action === 'reject' || action === 'implement') {
      this.store.setVotingClosed(id, true);
    }
    if (action === 'reopen') {
      this.store.setVotingClosed(id, false);
      const closesAt =
        this.cfg.voting.durationDays > 0
          ? Date.now() + this.cfg.voting.durationDays * 86_400_000
          : null;
      this.store.setVotingClosesAt(id, closesAt);
    }

    this.store.addModLog({
      suggestionId: id,
      staffId,
      action: ACTION_LABEL[action],
      reason,
      createdAt: Date.now(),
    });

    return this.store.getSuggestion(id)!;
  }
}
