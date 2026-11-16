import { afterEach, describe, expect, it } from 'vitest';
import { VotingService, VoteClosedError } from '../src/services/voting/votingService.js';
import type { Suggestion } from '../src/types/index.js';
import { memStore } from './helpers.js';
import type { SqliteStore } from '../src/database/sqlite.js';

function seed(store: SqliteStore, over: Partial<Suggestion> = {}): void {
  const now = Date.now();
  store.createSuggestion({
    id: 'RS-001',
    guildId: 'g',
    forumPostId: 'f1',
    authorId: 'a',
    originalTitle: 'T',
    originalDescription: 'D',
    aiTitle: null,
    aiSummary: null,
    aiStatus: 'fallback',
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
  });
}

describe('VotingService', () => {
  let store: SqliteStore;
  afterEach(() => store?.close());

  it('records up and down votes', async () => {
    store = memStore();
    seed(store);
    const svc = new VotingService(store);
    expect((await svc.handle('RS-001', 'u1', 'up')).totals.upvotes).toBe(1);
    expect((await svc.handle('RS-001', 'u2', 'down')).totals.downvotes).toBe(1);
  });

  it('changes a vote instead of duplicating', async () => {
    store = memStore();
    seed(store);
    const svc = new VotingService(store);
    await svc.handle('RS-001', 'u1', 'up');
    const out = await svc.handle('RS-001', 'u1', 'down');
    expect(out.totals.upvotes).toBe(0);
    expect(out.totals.downvotes).toBe(1);
    expect(out.totals.total).toBe(1);
  });

  it('toggles the same vote off', async () => {
    store = memStore();
    seed(store);
    const svc = new VotingService(store);
    await svc.handle('RS-001', 'u1', 'up');
    const out = await svc.handle('RS-001', 'u1', 'up');
    expect(out.totals.total).toBe(0);
  });

  it('clears a vote explicitly', async () => {
    store = memStore();
    seed(store);
    const svc = new VotingService(store);
    await svc.handle('RS-001', 'u1', 'up');
    const out = await svc.handle('RS-001', 'u1', 'clear');
    expect(out.totals.total).toBe(0);
    expect(out.feedback).toMatch(/removed/i);
  });

  it('rejects votes on a closed voting', async () => {
    store = memStore();
    seed(store, { votingClosed: 1 });
    const svc = new VotingService(store);
    await expect(svc.handle('RS-001', 'u1', 'up')).rejects.toBeInstanceOf(VoteClosedError);
  });

  it('serializes concurrent votes without losing counts', async () => {
    store = memStore();
    seed(store);
    const svc = new VotingService(store);
    await Promise.all(
      Array.from({ length: 20 }, (_, i) => svc.handle('RS-001', `user${i}`, 'up')),
    );
    expect(store.getTotals('RS-001').upvotes).toBe(20);
  });
});
