import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { SqliteStore } from '../src/database/sqlite.js';
import type { Suggestion } from '../src/types/index.js';

function sample(id: string, over: Partial<Suggestion> = {}): Suggestion {
  const now = Date.now();
  return {
    id,
    guildId: 'g',
    forumPostId: `f-${id}`,
    authorId: 'a',
    originalTitle: 'T',
    originalDescription: 'D',
    aiTitle: 'AI T',
    aiSummary: 'AI S',
    aiStatus: 'done',
    votingMessageId: 'msg1',
    votingChannelId: 'ch1',
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

describe('restart safety', () => {
  let dir: string;
  afterEach(() => dir && rmSync(dir, { recursive: true, force: true }));

  it('persists suggestions and votes across store reopen', () => {
    dir = mkdtempSync(join(tmpdir(), 'rsbot-'));
    const path = join(dir, 'test.db');

    let store = new SqliteStore(path);
    store.createSuggestion(sample('RS-001'));
    store.castVote('RS-001', 'u1', 1);
    store.castVote('RS-001', 'u2', 1);
    store.castVote('RS-001', 'u3', -1);
    store.close();

    // Simulate restart: reopen the same file.
    store = new SqliteStore(path);
    const s = store.getSuggestion('RS-001');
    expect(s).not.toBeNull();
    expect(s!.votingMessageId).toBe('msg1');
    const totals = store.getTotals('RS-001');
    expect(totals).toMatchObject({ upvotes: 2, downvotes: 1, total: 3 });

    // Button custom id resolution still works via voting message lookup.
    expect(store.getByVotingMessage('msg1')?.id).toBe('RS-001');
    // Dedupe still prevents re-publishing the same forum post.
    expect(store.getByForumPost('f-RS-001')?.id).toBe('RS-001');

    // Migrations are idempotent (no error on second open).
    store.close();
  });
});
