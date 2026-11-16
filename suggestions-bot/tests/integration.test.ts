import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { SqliteStore } from '../src/database/sqlite.js';
import { AiService } from '../src/services/ai/index.js';
import { SuggestionService } from '../src/services/suggestions/suggestionService.js';
import { VotingService, VoteClosedError } from '../src/services/voting/votingService.js';
import { ModerationService } from '../src/services/moderation/moderationService.js';
import { evaluate } from '../src/services/evaluation.js';
import { testConfig } from './helpers.js';

/**
 * End-to-end flow across all services, including a simulated unexpected restart
 * (store reopened from the same file), using only the deterministic fallback AI.
 */
describe('integration: full lifecycle with restart', () => {
  let dir: string;
  afterEach(() => dir && rmSync(dir, { recursive: true, force: true }));

  it('submit -> vote -> restart -> moderate stays consistent', async () => {
    dir = mkdtempSync(join(tmpdir(), 'rsbot-int-'));
    const path = join(dir, 'int.db');
    const cfg = testConfig({ databasePath: path });

    let store = new SqliteStore(path);
    const ai = new AiService(cfg); // provider "none" -> fallback
    const suggestions = new SuggestionService(cfg, store, ai);

    // 1) Member submits a forum post.
    const created = await suggestions.createFromForumPost({
      guildId: 'g',
      forumPostId: 'forum-1',
      authorId: 'member-1',
      title: 'Add a warp system',
      description:
        'It would be great to have a warp system so players can travel to spawn and shops quickly without walking.',
    });
    expect(created.created).toBe(true);
    expect(created.suggestion.id).toBe('RS-001');
    expect(created.suggestion.aiSummary).toBeTruthy();

    // Simulate the voting message id being persisted (done by Discord layer).
    store.setVotingMessage('RS-001', 'voting-channel', 'msg-123');

    // 2) Duplicate Discord event must not create a second suggestion.
    const dup = await suggestions.createFromForumPost({
      guildId: 'g',
      forumPostId: 'forum-1',
      authorId: 'member-1',
      title: 'Add a warp system',
      description: 'duplicate event body',
    });
    expect(dup.created).toBe(false);
    expect(store.stats().total).toBe(1);

    // 3) Voting.
    const voting = new VotingService(store);
    await voting.handle('RS-001', 'u1', 'up');
    await voting.handle('RS-001', 'u2', 'up');
    await voting.handle('RS-001', 'u3', 'down');
    await voting.handle('RS-001', 'u2', 'down'); // u2 changes vote
    expect(store.getTotals('RS-001')).toMatchObject({ upvotes: 1, downvotes: 2, total: 3 });

    // 4) UNEXPECTED RESTART — reopen the DB from disk.
    store.close();
    store = new SqliteStore(path);
    const voting2 = new VotingService(store);
    const moderation = new ModerationService(cfg, store);

    // Votes and ids survived.
    expect(store.getTotals('RS-001')).toMatchObject({ upvotes: 1, downvotes: 2 });
    expect(store.getByVotingMessage('msg-123')?.id).toBe('RS-001');
    expect(store.getByForumPost('forum-1')?.id).toBe('RS-001');

    // Voting still works after restart.
    await voting2.handle('RS-001', 'u4', 'up');
    expect(store.getTotals('RS-001').upvotes).toBe(2);

    // 5) Staff moderates: approve -> closes voting.
    const approved = moderation.apply('RS-001', 'staff-1', 'approve', 'community wants it');
    expect(approved.status).toBe('approved');
    expect(approved.votingClosed).toBe(1);

    // 6) Votes are rejected once closed.
    await expect(voting2.handle('RS-001', 'u5', 'up')).rejects.toBeInstanceOf(VoteClosedError);

    // 7) Evaluation is only guidance.
    const hints = evaluate(cfg, store.getTotals('RS-001'));
    expect(hints.join(' ')).toMatch(/staff decides/i);

    // Moderation history persisted.
    expect(store.listModLogs('RS-001')).toHaveLength(1);
    store.close();
  });
});
