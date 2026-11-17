import { config } from './config.js';
import { audit, recentTestResults } from './security/audit.js';
import { allow, remaining } from './security/rateLimit.js';
import { can, Principal, require as requireScope } from './security/rbac.js';
import { sessions } from './mc/sessionManager.js';
import { ActionRequest } from './mc/types.js';
import { renderScreenshot, schematicScreenshot, Screenshot } from './mc/viewer.js';
import { listTests, runAll, runTest } from './tests/scenarios.js';
import { status as authStatus } from './auth/microsoft.js';

/**
 * Central, permission-checked service used by BOTH the REST API (dashboard)
 * and the MCP server (ChatGPT). Keeps security in one place.
 */

function rate(p: Principal): void {
  if (!allow(p.id)) throw new Error('rate_limit_exceeded');
}

export async function startSession(p: Principal) {
  requireScope(p, 'admin'); // starting/stopping is an admin/control-plane op
  return sessions.start(p.id);
}

export async function stopSession(p: Principal, id?: string) {
  requireScope(p, 'admin');
  const target = id ?? sessions.current()?.id;
  if (!target) return { stopped: false, reason: 'no active session' };
  await sessions.stop(target, 'stopped via api');
  return { stopped: true, id: target };
}

export function getPlayerStatus(p: Principal, id?: string) {
  requireScope(p, 'observe');
  const bot = sessions.resolve(id);
  audit(p.kind === 'owner' ? 'owner' : 'ai', p.id, 'observe.player', 'ok', bot.sessionId);
  return bot.observe();
}

export function getServerStatus(p: Principal) {
  requireScope(p, 'observe');
  const current = sessions.current();
  return {
    service: 'ryze-test',
    target: `${config.MC_HOST}:${config.MC_PORT}`,
    mcVersion: config.MC_VERSION,
    authMode: config.MC_AUTH,
    authenticated: authStatus().authenticated,
    activeSessions: sessions.list(),
    maxSessions: config.MAX_SESSIONS,
    safetyMode: config.SAFETY_MODE,
    botConnected: current?.bot.connected ?? false,
    rateRemaining: remaining(p.id),
  };
}

export function inspectInventory(p: Principal, id?: string) {
  requireScope(p, 'observe');
  const bot = sessions.resolve(id);
  audit(p.kind === 'owner' ? 'owner' : 'ai', p.id, 'observe.inventory', 'ok', bot.sessionId);
  return { items: bot.inventory(), heldItem: bot.observe().heldItem };
}

export async function getScreenshot(p: Principal, id?: string): Promise<Screenshot> {
  requireScope(p, 'observe');
  const bot = sessions.resolve(id);
  audit(p.kind === 'owner' ? 'owner' : 'ai', p.id, 'observe.screenshot', 'ok', bot.sessionId);
  if (config.VIEWER_ENABLED) {
    const real = await renderScreenshot(bot.raw);
    if (real) return real;
  }
  return schematicScreenshot(bot.observe());
}

export async function sendChatMessage(p: Principal, message: string, id?: string) {
  requireScope(p, 'control');
  rate(p);
  if (typeof message !== 'string' || !message.trim()) throw new Error('empty message');
  if (message.length > 256) throw new Error('message too long (max 256)');
  return sessions.requestChat(id, message, p.id, can(p, 'admin'));
}

export async function executePlayerAction(p: Principal, action: ActionRequest, id?: string) {
  requireScope(p, 'control');
  rate(p);
  const bot = sessions.resolve(id);
  const before = bot.observe();
  const result = await bot.act(action);
  const after = bot.observe();
  audit(p.kind === 'owner' ? 'owner' : 'ai', p.id, `action.${action.type}`, result.ok ? 'ok' : 'error', bot.sessionId, result.message);
  return { result, before, after };
}

export function getTests(p: Principal) {
  requireScope(p, 'observe');
  return { available: listTests(), recent: recentTestResults(50) };
}

export async function runTestByName(p: Principal, name: string, id?: string) {
  requireScope(p, 'control');
  rate(p);
  const bot = sessions.resolve(id);
  audit(p.kind === 'owner' ? 'owner' : 'ai', p.id, 'test.run', 'ok', bot.sessionId, name);
  if (name === 'all' || name === '*') return { results: await runAll(bot) };
  return { results: [await runTest(bot, name)] };
}

export function getTestResults(p: Principal) {
  requireScope(p, 'observe');
  return recentTestResults(100);
}
