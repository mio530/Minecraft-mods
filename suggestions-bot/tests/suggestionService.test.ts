import { afterEach, describe, expect, it } from 'vitest';
import { AiService } from '../src/services/ai/index.js';
import { SuggestionService } from '../src/services/suggestions/suggestionService.js';
import { FakeProvider, ThrowingProvider, memStore, testConfig } from './helpers.js';
import type { SqliteStore } from '../src/database/sqlite.js';

describe('SuggestionService', () => {
  let store: SqliteStore;
  afterEach(() => store?.close());

  it('creates a suggestion with AI summary and RS-001 id', async () => {
    store = memStore();
    const cfg = testConfig();
    const ai = new AiService(cfg, new FakeProvider());
    const svc = new SuggestionService(cfg, store, ai);

    const res = await svc.createFromForumPost({
      guildId: 'g',
      forumPostId: 'f1',
      authorId: 'a1',
      title: 'Add more mobs',
      description: 'please add bees and frogs to the server world',
    });

    expect(res.created).toBe(true);
    expect(res.suggestion.id).toBe('RS-001');
    expect(res.suggestion.aiStatus).toBe('done');
    expect(res.suggestion.aiTitle).toContain('Add more mobs');
  });

  it('is idempotent for duplicate forum events', async () => {
    store = memStore();
    const cfg = testConfig();
    const fake = new FakeProvider();
    const svc = new SuggestionService(cfg, store, new AiService(cfg, fake));

    const first = await svc.createFromForumPost({
      guildId: 'g',
      forumPostId: 'same',
      authorId: 'a',
      title: 'Title',
      description: 'Body text',
    });
    const second = await svc.createFromForumPost({
      guildId: 'g',
      forumPostId: 'same',
      authorId: 'a',
      title: 'Title',
      description: 'Body text',
    });

    expect(first.created).toBe(true);
    expect(second.created).toBe(false);
    expect(second.suggestion.id).toBe(first.suggestion.id);
    expect(fake.calls).toBe(1); // AI only called once
  });

  it('falls back when the AI provider throws', async () => {
    store = memStore();
    const cfg = testConfig({
      ai: { ...testConfig().ai, provider: 'openai', apiKey: 'x' },
    });
    const ai = new AiService(cfg, new ThrowingProvider());
    const svc = new SuggestionService(cfg, store, ai);

    const res = await svc.createFromForumPost({
      guildId: 'g',
      forumPostId: 'f1',
      authorId: 'a',
      title: 'Broken AI',
      description: 'This sentence should still be summarized deterministically.',
    });

    expect(res.suggestion.aiStatus).toBe('failed');
    expect(res.suggestion.aiSummary).toBeTruthy();
  });

  it('detects near-duplicate suggestions', async () => {
    store = memStore();
    const cfg = testConfig({ ai: { ...testConfig().ai, similarityThreshold: 0.5 } });
    const svc = new SuggestionService(cfg, store, new AiService(cfg, new FakeProvider()));

    await svc.createFromForumPost({
      guildId: 'g',
      forumPostId: 'f1',
      authorId: 'a',
      title: 'Add a teleport command',
      description: 'We want a teleport command to travel between bases quickly',
    });
    const res = await svc.createFromForumPost({
      guildId: 'g',
      forumPostId: 'f2',
      authorId: 'b',
      title: 'Teleport command please',
      description: 'Please add a teleport command to travel between bases quickly',
    });

    expect(res.duplicates.length).toBeGreaterThan(0);
    expect(res.duplicates[0].suggestion.id).toBe('RS-001');
  });
});
