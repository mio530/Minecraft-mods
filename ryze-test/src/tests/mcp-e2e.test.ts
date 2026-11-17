import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

// Full end-to-end: Claude Code acting as a REAL MCP client over HTTP against
// the running ryze-test MCP server, driving a (simulated-protocol) bot through
// the exact same stack ChatGPT would use. The Minecraft protocol is simulated
// because this sandbox's egress to the live server + Xbox auth is blocked; the
// transport, client, server, service and observe/act loop are 100% real.

process.env.DB_PATH = join(mkdtempSync(join(tmpdir(), 'ryze-e2e-')), 'audit.db');
process.env.JWT_SECRET = 'e2e_secret_key_1234567890';
process.env.MCP_TOKEN = 'e2e-mcp-token';
process.env.DASHBOARD_PASSWORD = 'pw123456';
process.env.MC_AUTH = 'offline';
process.env.PORT = '25611';
process.env.HOST = '127.0.0.1';

let mcp: Awaited<ReturnType<typeof import('../mcp/client.js').connectMcp>>;
let srv: Awaited<ReturnType<typeof import('../index.js').startServer>>;

beforeAll(async () => {
  const { startServer } = await import('../index.js');
  const { sessions } = await import('../mc/sessionManager.js');
  const { McBot } = await import('../mc/bot.js');
  const { FakeBot } = await import('./fakeBot.js');
  const { connectMcp } = await import('../mcp/client.js');

  srv = await startServer();
  const bot = new McBot('e2e01');
  bot.attachForTest(new FakeBot());
  sessions.injectForTest(bot);

  mcp = await connectMcp(`${srv.url}/mcp`, 'e2e-mcp-token');
}, 20000);

afterAll(async () => {
  await mcp?.close();
  await srv?.close();
});

describe('Claude Code ⇄ MCP server (real HTTP client)', () => {
  it('lists all control tools', async () => {
    const tools = await mcp.listTools();
    for (const t of ['get_server_status', 'get_player_status', 'get_game_screenshot', 'inspect_inventory', 'send_chat_message', 'execute_player_action', 'start_test_session', 'stop_test_session', 'get_test_results', 'run_gameplay_test']) {
      expect(tools).toContain(t);
    }
  });

  it('reads server + player status over MCP', async () => {
    const s = await mcp.call<any>('get_server_status');
    expect(s.mcVersion).toBe('1.21.11');
    expect(s.activeSessions.length).toBe(1);
    const p = await mcp.call<any>('get_player_status');
    expect(p.connected).toBe(true);
    expect(p.health).toBe(20);
  });

  it('inspects inventory over MCP', async () => {
    const inv = await mcp.call<any>('inspect_inventory');
    expect(inv.items.map((i: any) => i.name)).toContain('bread');
  });

  it('captures a screenshot (image content) over MCP', async () => {
    const res: any = await mcp.callRaw('get_game_screenshot');
    const img = res.content.find((c: any) => c.type === 'image');
    expect(img).toBeTruthy();
    expect(img.mimeType).toContain('image/');
    const svg = Buffer.from(img.data, 'base64').toString('utf8');
    expect(svg).toContain('RyzeSMP Test Client');
  });

  it('executes an action and OBSERVES the result (before/after) over MCP', async () => {
    const r = await mcp.call<any>('execute_player_action', { type: 'move', direction: 'forward', durationMs: 800 });
    expect(r.result.ok).toBe(true);
    const d = Math.hypot(r.after.position.x - r.before.position.x, r.after.position.z - r.before.position.z);
    expect(d).toBeGreaterThan(0.5);
  });

  it('still gates dangerous commands behind owner approval (over MCP)', async () => {
    const r = await mcp.call<any>('send_chat_message', { message: '/op Mio' });
    expect(r.approvalRequired).toBe(true);
    expect(r.sent).toBe(false);
  });

  it('runs a RyzeSMP gameplay test over MCP', async () => {
    const r = await mcp.call<any>('run_gameplay_test', { name: 'combat-attack' });
    expect(r.results[0].status).toBe('pass');
  });
});
