import { beforeAll, describe, expect, it } from 'vitest';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

// Configure a throwaway DB + predictable secrets BEFORE importing app modules
// (config.ts reads env at import time).
process.env.DB_PATH = join(mkdtempSync(join(tmpdir(), 'ryze-test-')), 'audit.db');
process.env.JWT_SECRET = 'integration_test_secret_key';
process.env.MCP_TOKEN = 'integration-mcp-token';
process.env.SAFETY_MODE = 'safe';
process.env.MC_AUTH = 'offline';

const mods = async () => ({
  audit: await import('../security/audit.js'),
  svc: await import('../service.js'),
  rbac: await import('../security/rbac.js'),
  sessionMgr: await import('../mc/sessionManager.js'),
  McBot: (await import('../mc/bot.js')).McBot,
  FakeBot: (await import('./fakeBot.js')).FakeBot,
});

let m: Awaited<ReturnType<typeof mods>>;
let ownerP: ReturnType<typeof import('../security/rbac.js').owner>;
let aiP: ReturnType<typeof import('../security/rbac.js').ai>;

beforeAll(async () => {
  m = await mods();
  m.audit.initAudit();
  ownerP = m.rbac.owner();
  aiP = m.rbac.ai('chatgpt-mcp'); // observe + control, NOT admin
  const bot = new m.McBot('itest01');
  bot.attachForTest(new m.FakeBot());
  m.sessionMgr.sessions.injectForTest(bot);
});

describe('service layer (what REST + MCP both call)', () => {
  it('reports server status', () => {
    const s = m.svc.getServerStatus(aiP);
    expect(s.service).toBe('ryze-test');
    expect(s.mcVersion).toBe('1.21.11');
    expect(s.activeSessions.length).toBe(1);
  });

  it('observes player status', () => {
    const o = m.svc.getPlayerStatus(aiP);
    expect(o.connected).toBe(true);
    expect(o.health).toBe(20);
    expect(o.position).toEqual({ x: 0, y: 64, z: 0 });
    expect(o.nearbyPlayers).toContain('Mio');
  });

  it('inspects inventory', () => {
    const inv = m.svc.inspectInventory(aiP);
    expect(inv.items.map((i: any) => i.name)).toContain('bread');
  });

  it('captures a screenshot (schematic)', async () => {
    const shot = await m.svc.getScreenshot(aiP);
    expect(shot.kind).toBe('schematic');
    const svg = Buffer.from(shot.base64, 'base64').toString('utf8');
    expect(svg).toContain('RyzeSMP Test Client');
    expect(svg).toContain('<svg');
  });

  it('executes an action and returns observations BEFORE and AFTER (observe→act→observe)', async () => {
    const r = await m.svc.executePlayerAction(aiP, { type: 'move', direction: 'forward', durationMs: 800 });
    expect(r.result.ok).toBe(true);
    const d = Math.hypot(r.after.position!.x - r.before.position!.x, r.after.position!.z - r.before.position!.z);
    expect(d).toBeGreaterThan(0.5); // the AI can verify its action had an effect
  });

  it('sends a harmless chat message', async () => {
    const r = await m.svc.sendChatMessage(aiP, 'hello ryzesmp');
    expect(r.sent).toBe(true);
    expect(r.approvalRequired).toBe(false);
  });

  it('gates a dangerous command behind owner approval', async () => {
    const r = await m.svc.sendChatMessage(aiP, '/op Mio');
    expect(r.sent).toBe(false);
    expect(r.approvalRequired).toBe(true);
    const approvals = m.sessionMgr.sessions.pendingApprovals();
    expect(approvals.length).toBe(1);
    // owner approves it
    const ok = await m.sessionMgr.sessions.resolveApproval(approvals[0].id, true, 'owner');
    expect(ok).toBe(true);
    expect(m.sessionMgr.sessions.pendingApprovals().length).toBe(0);
  });

  it('runs the predefined gameplay test suite', async () => {
    const { results } = await m.svc.runTestByName(aiP, 'all');
    const byName = Object.fromEntries(results.map((r: any) => [r.name, r.status]));
    expect(byName['connectivity']).toBe('pass');
    expect(byName['movement']).toBe('pass');
    expect(byName['chat-roundtrip']).toBe('pass');
    expect(byName['inventory-read']).toBe('pass');
    // destructive RyzeSMP tests are skipped in a batch run
    expect(byName['risecar-spawn']).toBe('skipped');
  }, 30000);

  it('registers RyzeSMP-specific tests (economy, rise car, ranks, cards, combat, commands)', async () => {
    const { available } = m.svc.getTests(aiP);
    const names = available.map((t: any) => t.name);
    for (const n of ['economy-balance', 'ranks-permission-enforced', 'risecar-available', 'cards-available', 'player-commands-help', 'combat-attack']) {
      expect(names).toContain(n);
    }
    // destructive ones are flagged
    expect(available.find((t: any) => t.name === 'risecar-spawn')?.destructive).toBe(true);
  });

  it('runs a RyzeSMP combat test (mob nearby → swing accepted)', async () => {
    const { results } = await m.svc.runTestByName(aiP, 'combat-attack');
    expect(results[0].status).toBe('pass');
  });

  it('blocks a destructive RyzeSMP test for the AI (owner approval required)', async () => {
    const { results } = await m.svc.runTestByName(aiP, 'risecar-spawn');
    expect(results[0].status).toBe('skipped');
    expect(results[0].actual).toMatch(/owner/i);
  });

  it('records actions in the audit log', () => {
    const rows = m.audit.recentAudit(50);
    expect(rows.some((r) => r.action.startsWith('action.'))).toBe(true);
    expect(rows.some((r) => r.action === 'chat.approval_requested')).toBe(true);
  });

  it('enforces scope separation (observer cannot control)', async () => {
    const observerOnly = m.rbac.ai('ro', ['observe']);
    expect(m.rbac.can(observerOnly, 'control')).toBe(false);
    // observe is allowed
    expect(() => m.svc.getPlayerStatus(observerOnly)).not.toThrow();
    // control is denied
    await expect(m.svc.executePlayerAction(observerOnly, { type: 'jumpOnce' })).rejects.toThrow(/scope/i);
  });
});
