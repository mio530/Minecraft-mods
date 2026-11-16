import type { BotConfig } from '../../config/index.js';
import type { SuggestionStore } from '../../database/index.js';
import type { Suggestion } from '../../types/index.js';
import { globalLocks } from '../../utils/locks.js';
import { basicCleanup, truncate } from '../../utils/text.js';
import type { AiService, DuplicateMatch } from '../ai/index.js';

export interface NewSuggestionInput {
  guildId: string;
  forumPostId: string;
  authorId: string;
  title: string;
  description: string;
}

export interface CreateResult {
  suggestion: Suggestion;
  created: boolean;
  duplicates: DuplicateMatch[];
}

function formatId(prefix: string, seq: number): string {
  return `${prefix}-${String(seq).padStart(3, '0')}`;
}

export class SuggestionService {
  constructor(
    private cfg: BotConfig,
    private store: SuggestionStore,
    private ai: AiService,
  ) {}

  /**
   * Idempotently create a suggestion from a forum post. Safe against duplicate
   * Discord events and restarts (deduped by forumPostId with a unique index).
   */
  async createFromForumPost(input: NewSuggestionInput): Promise<CreateResult> {
    return globalLocks.run(`forum:${input.forumPostId}`, async () => {
      const existing = this.store.getByForumPost(input.forumPostId);
      if (existing) {
        return { suggestion: existing, created: false, duplicates: [] };
      }

      const seq = this.store.nextSequence(input.guildId);
      const id = formatId(this.cfg.suggestionIdPrefix, seq);
      const now = Date.now();
      const closesAt =
        this.cfg.voting.durationDays > 0
          ? now + this.cfg.voting.durationDays * 86_400_000
          : null;

      const title = truncate(basicCleanup(input.title || 'Untitled suggestion'), 256);
      const description = basicCleanup(input.description || input.title || '');

      const suggestion: Suggestion = {
        id,
        guildId: input.guildId,
        forumPostId: input.forumPostId,
        authorId: input.authorId,
        originalTitle: title,
        originalDescription: description,
        aiTitle: null,
        aiSummary: null,
        aiStatus: 'pending',
        votingMessageId: null,
        votingChannelId: null,
        status: 'pending',
        upvotes: 0,
        downvotes: 0,
        decidedBy: null,
        decisionReason: null,
        votingClosesAt: closesAt,
        votingClosed: 0,
        createdAt: now,
        updatedAt: now,
      };
      this.store.createSuggestion(suggestion);

      // AI summary (only here — cached in DB, never re-run on restart).
      const ai = await this.ai.summarize({ title, description });
      this.store.updateAi(id, ai.title, ai.summary, ai.status);

      // Duplicate detection against recent suggestions (local, no API cost).
      const recent = this.store.recentForDuplicateCheck(50);
      const duplicates = this.ai.findDuplicates({ title, description }, recent, id);

      return { suggestion: this.store.getSuggestion(id)!, created: true, duplicates };
    });
  }
}
