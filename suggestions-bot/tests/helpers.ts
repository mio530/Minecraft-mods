import type { BotConfig } from '../src/config/index.js';
import { SqliteStore } from '../src/database/sqlite.js';
import type { AiProvider, SummarizeInput } from '../src/services/ai/provider.js';
import type { AiResult } from '../src/types/index.js';

export function testConfig(overrides: Partial<BotConfig> = {}): BotConfig {
  return {
    discord: { token: 't', clientId: 'c', guildId: 'g' },
    channels: { submission: 'sub', voting: 'vote', log: 'log' },
    staffRoleIds: ['staff-role'],
    ai: {
      provider: 'none',
      apiKey: '',
      model: 'test',
      rateLimitPerMinute: 10,
      similarityThreshold: 0.82,
    },
    voting: {
      durationDays: 7,
      minimumVotes: 3,
      minimumUpvotes: 2,
      minimumPositiveRatio: 60,
      evaluationIntervalMinutes: 15,
    },
    databasePath: ':memory:',
    suggestionIdPrefix: 'RS',
    logLevel: 'error',
    ...overrides,
  };
}

export function memStore(): SqliteStore {
  return new SqliteStore(':memory:');
}

/** A fake AI provider that records calls and returns a canned result. */
export class FakeProvider implements AiProvider {
  readonly name = 'fake';
  calls = 0;
  constructor(private result?: Partial<AiResult>) {}
  async summarize(input: SummarizeInput): Promise<AiResult> {
    this.calls++;
    return {
      title: this.result?.title ?? `AI: ${input.title}`,
      summary: this.result?.summary ?? `Summary of ${input.description}`,
      status: this.result?.status ?? 'done',
    };
  }
}

/** A provider that always throws, to test the fallback path. */
export class ThrowingProvider implements AiProvider {
  readonly name = 'throwing';
  async summarize(): Promise<AiResult> {
    throw new Error('provider down');
  }
}
