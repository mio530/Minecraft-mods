export type SuggestionStatus =
  | 'pending'
  | 'approved'
  | 'rejected'
  | 'implemented'
  | 'closed';

export type AiStatus = 'pending' | 'done' | 'fallback' | 'failed';

export type VoteValue = 1 | -1;

export interface Suggestion {
  /** Public, human-readable id, e.g. RS-001 */
  id: string;
  guildId: string;
  /** Forum thread/post id in the submission channel */
  forumPostId: string;
  authorId: string;
  originalTitle: string;
  originalDescription: string;
  aiTitle: string | null;
  aiSummary: string | null;
  aiStatus: AiStatus;
  /** Message id of the voting message in the voting channel */
  votingMessageId: string | null;
  votingChannelId: string | null;
  status: SuggestionStatus;
  upvotes: number;
  downvotes: number;
  /** Staff id that made the last moderation decision */
  decidedBy: string | null;
  decisionReason: string | null;
  /** Epoch ms when voting should auto-close (null = never) */
  votingClosesAt: number | null;
  votingClosed: 0 | 1;
  createdAt: number;
  updatedAt: number;
}

export interface Vote {
  suggestionId: string;
  userId: string;
  value: VoteValue;
  createdAt: number;
  updatedAt: number;
}

export interface ModerationLog {
  id?: number;
  suggestionId: string;
  staffId: string;
  action: string;
  reason: string | null;
  createdAt: number;
}

export interface VoteTotals {
  upvotes: number;
  downvotes: number;
  total: number;
  positiveRatio: number;
}

export interface AiResult {
  title: string;
  summary: string;
  status: AiStatus;
}
