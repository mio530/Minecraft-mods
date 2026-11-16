import type { Client } from 'discord.js';
import { afterEach, describe, expect, it } from 'vitest';
import { BotContext } from '../src/bot/context.js';
import type { SqliteStore } from '../src/database/sqlite.js';
import { memStore, testConfig } from './helpers.js';

/** channelId() only touches config + store, so a dummy client is fine. */
function ctxWith(store: SqliteStore, cfg = testConfig()) {
  return new BotContext({} as unknown as Client, cfg, store);
}

describe('BotContext.channelId resolution', () => {
  let store: SqliteStore;
  afterEach(() => store?.close());

  it('falls back to env config when nothing is in the database', () => {
    store = memStore();
    const ctx = ctxWith(store, testConfig({ channels: { submission: 'env-sub', voting: 'env-vote', log: 'env-log' } }));
    expect(ctx.channelId('submission')).toBe('env-sub');
    expect(ctx.channelId('voting')).toBe('env-vote');
    expect(ctx.channelId('log')).toBe('env-log');
  });

  it('prefers database values over env config', () => {
    store = memStore();
    const cfg = testConfig({ channels: { submission: 'env-sub', voting: 'env-vote', log: 'env-log' } });
    store.saveGuildSettings(cfg.discord.guildId, {
      submissionChannelId: 'db-sub',
      votingChannelId: 'db-vote',
      logChannelId: 'db-log',
    });
    const ctx = ctxWith(store, cfg);
    expect(ctx.channelId('submission')).toBe('db-sub');
    expect(ctx.channelId('voting')).toBe('db-vote');
    expect(ctx.channelId('log')).toBe('db-log');
  });

  it('returns empty string when nothing is configured', () => {
    store = memStore();
    const ctx = ctxWith(store, testConfig({ channels: { submission: '', voting: '', log: '' } }));
    expect(ctx.channelId('voting')).toBe('');
  });
});
