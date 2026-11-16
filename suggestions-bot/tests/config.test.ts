import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { loadConfig, resetConfigCache } from '../src/config/index.js';

const REQUIRED = {
  DISCORD_TOKEN: 'token',
  DISCORD_CLIENT_ID: 'client',
  DISCORD_GUILD_ID: 'guild',
  SUBMISSION_CHANNEL_ID: 'sub',
  VOTING_CHANNEL_ID: 'vote',
  LOG_CHANNEL_ID: 'log',
};

describe('loadConfig', () => {
  const saved = { ...process.env };
  beforeEach(() => {
    resetConfigCache();
    for (const k of Object.keys(process.env)) {
      if (k.startsWith('DISCORD_') || k.endsWith('_CHANNEL_ID') || k.startsWith('AI_') || k.startsWith('STAFF') || k.startsWith('VOTING') || k.startsWith('MINIMUM')) {
        delete process.env[k];
      }
    }
    Object.assign(process.env, REQUIRED);
  });
  afterEach(() => {
    process.env = { ...saved };
    resetConfigCache();
  });

  it('loads required values and parses staff roles', () => {
    process.env.STAFF_ROLE_IDS = 'a, b ,c';
    const cfg = loadConfig();
    expect(cfg.discord.token).toBe('token');
    expect(cfg.staffRoleIds).toEqual(['a', 'b', 'c']);
    expect(cfg.ai.provider).toBe('none');
  });

  it('throws when a required var is missing', () => {
    delete process.env.DISCORD_TOKEN;
    expect(() => loadConfig()).toThrow(/DISCORD_TOKEN/);
  });

  it('requires an API key when a provider is set', () => {
    process.env.AI_PROVIDER = 'openai';
    expect(() => loadConfig()).toThrow(/AI_API_KEY/);
  });

  it('rejects an invalid provider', () => {
    process.env.AI_PROVIDER = 'bogus';
    expect(() => loadConfig()).toThrow(/AI_PROVIDER/);
  });
});
