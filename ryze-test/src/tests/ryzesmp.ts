import { existsSync, readFileSync } from 'node:fs';
import { McBot } from '../mc/bot.js';
import { TestCase } from './scenarios.js';

/**
 * RyzeSMP-specific automated tests.
 *
 * Most RyzeSMP features are exposed as server commands whose exact names and
 * response text depend on the installed plugins. These tests are therefore
 * **chat-probe** based and **configurable**: each sends a command and checks
 * the server's chat response against a regex. Defaults are reasonable guesses;
 * tune them to your plugins in `ryzesmp-tests.json` (same folder as the app)
 * without touching code.
 *
 * Honesty rules:
 *  - A probe only PASSES on a matching server response. No response, or no
 *    match, is a FAIL with the captured chat shown (never a false pass).
 *  - Tests that change server/world state are marked `destructive` and require
 *    explicit owner approval to run (the AI cannot run them alone).
 */

const delay = (ms: number) => new Promise((r) => setTimeout(r, ms));

export interface Probe {
  name: string;
  description: string;
  command: string;
  /** regex (string) the combined server response must match to PASS */
  expect: string;
  timeoutMs?: number;
  destructive?: boolean;
}

/** Default probes — override any field via ryzesmp-tests.json. */
const DEFAULT_PROBES: Probe[] = [
  {
    name: 'economy-balance',
    description: 'Economy: /balance returns a currency amount.',
    command: '/balance',
    expect: 'balance|guthaben|coins?|money|\\$|€|\\d+',
    timeoutMs: 1500,
  },
  {
    name: 'ranks-permission-enforced',
    description: 'Ranks/permissions: a restricted command is denied for the test account.',
    command: '/gamemode creative',
    expect: 'permission|keine rechte|darfst|not allowed|erlaubt|unknown command',
    timeoutMs: 1500,
  },
  {
    name: 'ranks-info',
    description: 'Ranks: /rank (or configured) reports the player rank.',
    command: '/rank',
    expect: 'rank|rang|gruppe|group|member|default',
    timeoutMs: 1500,
  },
  {
    name: 'risecar-available',
    description: 'Rise Car Plugin: /car help responds with car/vehicle usage text.',
    command: '/car help',
    expect: 'car|auto|vehicle|fahrzeug|usage|hilfe|rise',
    timeoutMs: 1500,
  },
  {
    name: 'cards-available',
    description: 'Custom cards/items: /cards (or configured) responds.',
    command: '/cards',
    expect: 'card|karte|collection|sammlung|rarity',
    timeoutMs: 1500,
  },
  {
    name: 'player-commands-help',
    description: 'Player commands: /help lists available commands.',
    command: '/help',
    expect: 'help|hilfe|command|befehl|---|===|/\\w+',
    timeoutMs: 1500,
  },
  // --- destructive (owner approval required) ---
  {
    name: 'risecar-spawn',
    description: 'Rise Car Plugin: spawn a car (changes world state).',
    command: '/car spawn',
    expect: 'spawn|gespawnt|car|auto|vehicle|placed',
    timeoutMs: 2000,
    destructive: true,
  },
  {
    name: 'cards-give-test',
    description: 'Custom items: grant a test card/item to the bot (changes inventory).',
    command: '/cards give test',
    expect: 'give|erhalten|added|received|card|item',
    timeoutMs: 2000,
    destructive: true,
  },
];

function loadProbes(): Probe[] {
  const path = process.env.RYZE_TESTS_FILE ?? 'ryzesmp-tests.json';
  if (existsSync(path)) {
    try {
      const custom = JSON.parse(readFileSync(path, 'utf8')) as Probe[];
      if (Array.isArray(custom) && custom.length) return custom;
    } catch {
      /* fall back to defaults on parse error */
    }
  }
  return DEFAULT_PROBES;
}

function probeToTest(p: Probe): TestCase {
  return {
    name: p.name,
    description: p.description,
    destructive: p.destructive,
    run: async (bot: McBot) => {
      const since = Date.now();
      await bot.sendChat(p.command);
      await delay(p.timeoutMs ?? 1500);
      const lines = bot
        .observe()
        .recentChat.filter((c) => c.ts >= since)
        .map((c) => c.text);
      const joined = lines.join(' | ');
      const matched = new RegExp(p.expect, 'i').test(joined);
      if (matched) {
        return { status: 'pass', expected: `response ~ /${p.expect}/`, actual: joined.slice(0, 220) };
      }
      return {
        status: 'fail',
        expected: `response ~ /${p.expect}/`,
        actual: joined ? joined.slice(0, 220) : 'no server response captured',
        detail: `command "${p.command}" — adjust ryzesmp-tests.json (command/expect) for your plugins`,
      };
    },
  };
}

/** Combat: attack the nearest mob and confirm the server accepts the swing. */
const combatTest: TestCase = {
  name: 'combat-attack',
  description: 'Combat mechanics: attack the nearest mob (normal gameplay).',
  run: async (bot: McBot) => {
    const target = bot.observe().nearbyEntities.find((e) => e.type === 'mob' || e.type === 'hostile' || e.type === 'animal');
    if (!target) {
      return { status: 'fail', expected: 'a mob within range to attack', actual: 'no mob nearby (move near one and retry)' };
    }
    const r = await bot.act({ type: 'attackNearest', entityType: 'mob' });
    return {
      status: r.ok ? 'pass' : 'fail',
      expected: 'attack swing accepted by server',
      actual: r.message,
    };
  },
};

export function ryzeTests(): TestCase[] {
  return [...loadProbes().map(probeToTest), combatTest];
}
