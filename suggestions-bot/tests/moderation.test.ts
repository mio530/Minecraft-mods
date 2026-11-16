import { afterEach, describe, expect, it } from 'vitest';
import {
  ModerationService,
  SuggestionNotFoundError,
} from '../src/services/moderation/moderationService.js';
import type { Suggestion } from '../src/types/index.js';
import { memStore, testConfig } from './helpers.js';
import type { SqliteStore } from '../src/database/sqlite.js';

function seed(store: SqliteStore): void {
  const now = Date.now();
  const s: Suggestion = {
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
    votingClosesAt: Date.now() + 1000,
    votingClosed: 0,
    createdAt: now,
    updatedAt: now,
  };
  store.createSuggestion(s);
}

describe('ModerationService', () => {
  let store: SqliteStore;
  afterEach(() => store?.close());

  it('approves and closes voting + logs', () => {
    store = memStore();
    seed(store);
    const svc = new ModerationService(testConfig(), store);
    const updated = svc.apply('RS-001', 'staff1', 'approve', 'looks good');
    expect(updated.status).toBe('approved');
    expect(updated.votingClosed).toBe(1);
    expect(updated.decidedBy).toBe('staff1');
    expect(store.listModLogs('RS-001')).toHaveLength(1);
  });

  it('reopens a closed suggestion and restarts voting window', () => {
    store = memStore();
    seed(store);
    const svc = new ModerationService(testConfig(), store);
    svc.apply('RS-001', 's', 'close', null);
    const reopened = svc.apply('RS-001', 's', 'reopen', null);
    expect(reopened.status).toBe('pending');
    expect(reopened.votingClosed).toBe(0);
    expect(reopened.votingClosesAt).toBeGreaterThan(Date.now());
  });

  it('end only closes voting, keeps status', () => {
    store = memStore();
    seed(store);
    const svc = new ModerationService(testConfig(), store);
    const updated = svc.apply('RS-001', 's', 'end', null);
    expect(updated.status).toBe('pending');
    expect(updated.votingClosed).toBe(1);
  });

  it('throws for unknown suggestion', () => {
    store = memStore();
    const svc = new ModerationService(testConfig(), store);
    expect(() => svc.apply('RS-999', 's', 'approve', null)).toThrow(SuggestionNotFoundError);
  });
});
