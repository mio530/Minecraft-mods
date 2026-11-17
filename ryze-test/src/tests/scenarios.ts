import { McBot } from '../mc/bot.js';
import { recordTestResult } from '../security/audit.js';
import { Observation } from '../mc/types.js';
import { ryzeTests } from './ryzesmp.js';

export interface TestCase {
  name: string;
  description: string;
  /** destructive tests require owner approval to run */
  destructive?: boolean;
  run: (bot: McBot) => Promise<{ status: 'pass' | 'fail' | 'error'; expected: string; actual: string; detail?: string }>;
}

function delay(ms: number) {
  return new Promise((r) => setTimeout(r, ms));
}

/**
 * Core engine/gameplay tests. Each follows the observe → act → observe →
 * verify loop and records its result. RyzeSMP-specific tests are appended
 * from ./ryzesmp (see bottom of file).
 */
const CORE_TESTS: TestCase[] = [
  {
    name: 'connectivity',
    description: 'Bot is connected to RyzeSMP and receiving player state.',
    run: async (bot) => {
      const o = bot.observe();
      const ok = o.connected && o.health != null && o.position != null;
      return {
        status: ok ? 'pass' : 'fail',
        expected: 'connected with health & position',
        actual: `connected=${o.connected} health=${o.health} pos=${JSON.stringify(o.position)}`,
      };
    },
  },
  {
    name: 'server-status',
    description: 'Dimension and game mode are reported by the server.',
    run: async (bot) => {
      const o = bot.observe();
      const ok = !!o.dimension && !!o.gameMode;
      return {
        status: ok ? 'pass' : 'fail',
        expected: 'dimension and gameMode present',
        actual: `dim=${o.dimension} mode=${o.gameMode}`,
      };
    },
  },
  {
    name: 'movement',
    description: 'Walking forward changes the player position.',
    run: async (bot) => {
      const before = bot.observe().position;
      await bot.act({ type: 'move', direction: 'forward', durationMs: 800 });
      await delay(200);
      const after = bot.observe().position;
      const moved = before && after ? dist(before, after) : 0;
      return {
        status: moved > 0.5 ? 'pass' : 'fail',
        expected: 'position delta > 0.5 blocks',
        actual: `moved ${moved.toFixed(2)} blocks`,
        detail: `from ${JSON.stringify(before)} to ${JSON.stringify(after)}`,
      };
    },
  },
  {
    name: 'look-control',
    description: 'Setting the view yaw actually rotates the player.',
    run: async (bot) => {
      await bot.act({ type: 'look', yawDeg: 0, pitchDeg: 0 });
      await delay(150);
      await bot.act({ type: 'look', yawDeg: 90, pitchDeg: 0 });
      await delay(150);
      const yaw = bot.observe().yaw ?? 0;
      const ok = Math.abs(Math.abs(yaw) - Math.PI / 2) < 0.3;
      return {
        status: ok ? 'pass' : 'fail',
        expected: 'yaw ≈ 90° (1.57 rad)',
        actual: `yaw=${yaw.toFixed(2)} rad`,
      };
    },
  },
  {
    name: 'inventory-read',
    description: 'Inventory can be inspected (read-only).',
    run: async (bot) => {
      const inv = bot.inventory();
      return {
        status: Array.isArray(inv) ? 'pass' : 'error',
        expected: 'inventory array returned',
        actual: `${inv.length} item stack(s)`,
        detail: inv.map((i) => `${i.name}x${i.count}`).join(', '),
      };
    },
  },
  {
    name: 'chat-roundtrip',
    description: 'A harmless chat message is accepted and echoed back by the server.',
    run: async (bot) => {
      const marker = `ryze-test-${Date.now() % 100000}`;
      await bot.sendChat(marker);
      await delay(1200);
      const seen = bot.observe().recentChat.some((c) => c.text.includes(marker));
      return {
        status: seen ? 'pass' : 'fail',
        expected: `chat echo containing "${marker}"`,
        actual: seen ? 'echo observed' : 'no echo within 1.2s',
      };
    },
  },
];

// Combined registry: core engine tests + RyzeSMP feature tests.
export const TESTS: TestCase[] = [...CORE_TESTS, ...ryzeTests()];

export function isDestructiveTest(name: string): boolean {
  return !!TESTS.find((t) => t.name === name)?.destructive;
}

export function listTests(): { name: string; description: string; destructive: boolean }[] {
  return TESTS.map((t) => ({ name: t.name, description: t.description, destructive: !!t.destructive }));
}

export async function runTest(bot: McBot, name: string): Promise<{
  name: string;
  status: 'pass' | 'fail' | 'error' | 'skipped';
  expected: string;
  actual: string;
  detail?: string;
}> {
  const tc = TESTS.find((t) => t.name === name);
  if (!tc) return { name, status: 'error', expected: 'known test', actual: 'unknown test name' };
  try {
    const r = await tc.run(bot);
    recordTestResult({ sessionId: bot.sessionId, name, status: r.status, expected: r.expected, actual: r.actual, detail: r.detail ?? null });
    return { name, ...r };
  } catch (err) {
    const detail = err instanceof Error ? err.message : String(err);
    recordTestResult({ sessionId: bot.sessionId, name, status: 'error', expected: '-', actual: detail, detail: null });
    return { name, status: 'error', expected: '-', actual: detail };
  }
}

export async function runAll(bot: McBot): Promise<Array<{ name: string; status: string; expected: string; actual: string; detail?: string }>> {
  const out = [];
  for (const t of TESTS) {
    if (t.destructive) {
      out.push({ name: t.name, status: 'skipped', expected: t.description, actual: 'destructive test skipped in batch run' });
      continue;
    }
    out.push(await runTest(bot, t.name));
  }
  return out;
}

function dist(a: Observation['position'], b: Observation['position']): number {
  if (!a || !b) return 0;
  return Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z);
}
