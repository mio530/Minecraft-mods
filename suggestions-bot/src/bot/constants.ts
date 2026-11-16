import type { SuggestionStatus } from '../types/index.js';

export const BRAND = {
  name: 'RyzeSMP',
  footer: 'RyzeSMP • Suggestions',
  // Brand accent (greenish) used as the default embed color.
  color: 0x3ba55d,
} as const;

export const STATUS_META: Record<
  SuggestionStatus,
  { label: string; emoji: string; color: number }
> = {
  pending: { label: 'Pending', emoji: '🟡', color: 0xf1c40f },
  approved: { label: 'Approved', emoji: '🟢', color: 0x2ecc71 },
  rejected: { label: 'Rejected', emoji: '🔴', color: 0xe74c3c },
  implemented: { label: 'Implemented', emoji: '🔵', color: 0x3498db },
  closed: { label: 'Closed', emoji: '⚫', color: 0x95a5a6 },
};

export const VOTE_EMOJI = {
  up: '👍',
  down: '👎',
} as const;
