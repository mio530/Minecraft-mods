import { afterEach, describe, expect, it } from 'vitest';
import { SqliteStore } from '../src/database/sqlite.js';
import type { Suggestion } from '../src/types/index.js';
import { memStore } from './helpers.js';

function sample(id: string, over: Partial<Suggestion> = {}): Suggestion {
  const now = Date.now();
  return {
    id,
    guildId: 'g',
    forumPostId: `forum-${id}`,
    authorId: 'author',
    originalTitle: `Title ${id}`,
    originalDescription: `Description ${id}`,
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
    votingClosesAt: null,
    votingClosed: 0,
    createdAt: now,
    updatedAt: now,
    ...over,
  };
}

describe('SqliteStore', () => {
  let store: SqliteStore;
  afterEach(() => store?.close());

  it('generates sequential ids per guild', () => {
    store = memStore();
    expect(store.nextSequence('g')).toBe(1);
    expect(store.nextSequence('g')).toBe(2);
    expect(store.nextSequence('other')).toBe(1);
  });

  it('enforces unique forum post (dedupe)', () => {
    store = memStore();
    store.createSuggestion(sample('RS-001'));
    expect(() => store.createSuggestion(sample('RS-002', { forumPostId: 'forum-RS-001' }))).toThrow();
    expect(store.getByForumPost('forum-RS-001')?.id).toBe('RS-001');
  });

  it('casts, changes, toggles and removes votes with correct totals', () => {
    store = memStore();
    store.createSuggestion(sample('RS-001'));

    let t = store.castVote('RS-001', 'u1', 1);
    expect(t).toMatchObject({ upvotes: 1, downvotes: 0, total: 1, positiveRatio: 100 });

    // same user changes to downvote => still one vote
    t = store.castVote('RS-001', 'u1', -1);
    expect(t).toMatchObject({ upvotes: 0, downvotes: 1, total: 1 });

    // second user upvotes
    t = store.castVote('RS-001', 'u2', 1);
    expect(t).toMatchObject({ upvotes: 1, downvotes: 1, total: 2, positiveRatio: 50 });

    // remove u1
    t = store.removeVote('RS-001', 'u1');
    expect(t).toMatchObject({ upvotes: 1, downvotes: 0, total: 1 });

    // denormalized counts match
    const s = store.getSuggestion('RS-001')!;
    expect(s.upvotes).toBe(1);
    expect(s.downvotes).toBe(0);
  });

  it('zero votes gives 0% positive', () => {
    store = memStore();
    store.createSuggestion(sample('RS-001'));
    expect(store.getTotals('RS-001').positiveRatio).toBe(0);
  });

  it('records moderation logs', () => {
    store = memStore();
    store.createSuggestion(sample('RS-001'));
    store.addModLog({
      suggestionId: 'RS-001',
      staffId: 's1',
      action: 'Approved',
      reason: 'good',
      createdAt: Date.now(),
    });
    const logs = store.listModLogs('RS-001');
    expect(logs).toHaveLength(1);
    expect(logs[0]).toMatchObject({ action: 'Approved', staffId: 's1' });
  });

  it('computes stats and leaderboard', () => {
    store = memStore();
    store.createSuggestion(sample('RS-001'));
    store.createSuggestion(sample('RS-002'));
    store.updateStatus('RS-002', 'approved', 's', null);
    store.castVote('RS-001', 'u1', 1);
    store.castVote('RS-001', 'u2', 1);

    const stats = store.stats();
    expect(stats.total).toBe(2);
    expect(stats.approved).toBe(1);
    expect(stats.pending).toBe(1);

    const lb = store.leaderboard(10);
    expect(lb[0].suggestion.id).toBe('RS-001');
    expect(lb[0].score).toBe(2);
  });

  it('lists expired votings only when past closesAt and open', () => {
    store = memStore();
    const past = Date.now() - 1000;
    const future = Date.now() + 100000;
    store.createSuggestion(sample('RS-001', { votingClosesAt: past }));
    store.createSuggestion(sample('RS-002', { votingClosesAt: future }));
    store.createSuggestion(sample('RS-003', { votingClosesAt: past, votingClosed: 1 }));
    const expired = store.listExpired(Date.now());
    expect(expired.map((s) => s.id)).toEqual(['RS-001']);
  });
});
