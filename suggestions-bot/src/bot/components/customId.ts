/**
 * Stable custom IDs for persistent components. Format: `vote:<action>:<suggestionId>`.
 * These must stay stable across restarts so that persistent buttons keep working.
 */
export const CUSTOM_ID_PREFIX = 'rsvote';

export type VoteAction = 'up' | 'down' | 'clear';

export function buildVoteId(action: VoteAction, suggestionId: string): string {
  return `${CUSTOM_ID_PREFIX}:${action}:${suggestionId}`;
}

export function parseVoteId(
  customId: string,
): { action: VoteAction; suggestionId: string } | null {
  const parts = customId.split(':');
  if (parts.length !== 3 || parts[0] !== CUSTOM_ID_PREFIX) return null;
  const action = parts[1] as VoteAction;
  if (action !== 'up' && action !== 'down' && action !== 'clear') return null;
  return { action, suggestionId: parts[2] };
}
