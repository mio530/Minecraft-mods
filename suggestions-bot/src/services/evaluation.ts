import type { BotConfig } from '../config/index.js';
import type { VoteTotals } from '../types/index.js';

/**
 * Produce non-binding evaluation hints for staff. The bot NEVER auto-approves;
 * these are decision helpers only.
 */
export function evaluate(cfg: BotConfig, totals: VoteTotals): string[] {
  const hints: string[] = [];
  const v = cfg.voting;

  if (totals.total < v.minimumVotes) {
    hints.push(`Below minimum votes (${totals.total}/${v.minimumVotes}).`);
  } else {
    hints.push(`Reached minimum votes (${totals.total}/${v.minimumVotes}).`);
  }
  if (totals.upvotes >= v.minimumUpvotes) {
    hints.push(`Reached minimum upvotes (${totals.upvotes}/${v.minimumUpvotes}).`);
  }
  if (totals.positiveRatio >= v.minimumPositiveRatio) {
    hints.push(`Positive ratio met (${totals.positiveRatio}% ≥ ${v.minimumPositiveRatio}%).`);
  }

  const looksPositive =
    totals.total >= v.minimumVotes &&
    totals.upvotes >= v.minimumUpvotes &&
    totals.positiveRatio >= v.minimumPositiveRatio;
  hints.push(
    looksPositive
      ? '➡️ Community signal: POSITIVE (staff decides).'
      : '➡️ Community signal: inconclusive (staff decides).',
  );
  return hints;
}
