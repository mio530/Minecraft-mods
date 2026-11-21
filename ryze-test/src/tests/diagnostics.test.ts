import { describe, expect, it } from 'vitest';

process.env.DB_PATH = '/tmp/ryze-diag-test.db';
process.env.MC_HOST = '127.0.0.1';
process.env.MC_PORT = '25565';
process.env.JWT_SECRET = 'diag_test_secret_123456';
process.env.MCP_TOKEN = 'diag-mcp-token';
process.env.DASHBOARD_PASSWORD = 'pw123456';

const okTcp = async () => ({ ok: true, detail: 'TCP connect ok', ms: 5 });
const okHttps = async (url: string) => ({ ok: true, detail: 'HTTP 200', ms: 5 });
const okPing = async () => ({ ok: true, version: '1.21.11', protocol: 774, players: '0/20', detail: 'handshake ok, version 1.21.11', ms: 7 });
const failPing = async () => ({ ok: false, version: null, protocol: null, players: null, detail: 'ping timeout', ms: 8000 });
const anon403 = async (url: string) => ({ ok: true, detail: 'HTTP 403 (anonym normal)', ms: 5 });

describe('runDiagnostics', () => {
  it('probes the MC server plus all auth/join hosts', async () => {
    const { runDiagnostics } = await import('../mc/diagnostics.js');
    const r = await runDiagnostics({ tcp: okTcp as any, https: okHttps as any, mcPing: okPing as any });
    const targets = r.probes.map((p) => p.target);
    expect(targets).toContain('127.0.0.1:25565');
    expect(targets.some((t) => t.includes('sessionserver.mojang.com'))).toBe(true);
    expect(targets.some((t) => t.includes('api.minecraftservices.com'))).toBe(true);
    expect(r.probes.every((p) => p.ok)).toBe(true);
    expect(r.summary).toMatch(/Netzwerk ok|antwortet als Minecraft/);
  });

  it('pins the failure on sessionserver when only the join host is blocked', async () => {
    const { runDiagnostics } = await import('../mc/diagnostics.js');
    const https = async (url: string) =>
      url.includes('sessionserver.mojang.com')
        ? { ok: false, detail: 'ERR_STREAM_PREMATURE_CLOSE', ms: 10 }
        : { ok: true, detail: 'HTTP 200', ms: 5 };
    const r = await runDiagnostics({ tcp: okTcp as any, https: https as any, mcPing: okPing as any });
    const session = r.probes.find((p) => p.target.includes('sessionserver'))!;
    expect(session.ok).toBe(false);
    expect(r.summary).toMatch(/sessionserver\.mojang\.com/);
    expect(r.summary).toMatch(/Egress|Firewall|erreichbar/);
  });

  it('reports an unreachable Minecraft server distinctly', async () => {
    const { runDiagnostics } = await import('../mc/diagnostics.js');
    const tcp = async () => ({ ok: false, detail: 'ECONNREFUSED', ms: 3 });
    const r = await runDiagnostics({ tcp: tcp as any, https: okHttps as any, mcPing: okPing as any });
    expect(r.summary).toMatch(/nicht per TCP erreichbar|MC_HOST/);
  });

  it('includes a Minecraft status-ping probe and reports the server version', async () => {
    const { runDiagnostics } = await import('../mc/diagnostics.js');
    const r = await runDiagnostics({ tcp: okTcp as any, https: okHttps as any, mcPing: okPing as any });
    expect(r.probes.some((p) => p.name.includes('status ping'))).toBe(true);
    expect(r.summary).toMatch(/Minecraft 1\.21\.11/);
  });

  it('pins a TCP-open-but-no-handshake case on protocol/Velocity, NOT auth', async () => {
    const { runDiagnostics } = await import('../mc/diagnostics.js');
    const r = await runDiagnostics({ tcp: okTcp as any, https: okHttps as any, mcPing: failPing as any });
    expect(r.summary).toMatch(/KEIN Minecraft-Status-Handshake/);
    expect(r.summary).toMatch(/NICHT auf ein Auth-Problem/);
  });

  it('treats HTTP 403/404 from anonymous probes as reachable (not auth failure)', async () => {
    const { runDiagnostics } = await import('../mc/diagnostics.js');
    const r = await runDiagnostics({ tcp: okTcp as any, https: anon403 as any, mcPing: okPing as any });
    // every auth host returned 403 but is still reachable -> summary is the "ok" path
    expect(r.probes.filter((p) => p.detail.includes('403')).every((p) => p.ok)).toBe(true);
    expect(r.summary).toMatch(/Netzwerk ok/);
  });

  it('never includes anything token-shaped', async () => {
    const { runDiagnostics } = await import('../mc/diagnostics.js');
    const r = await runDiagnostics({ tcp: okTcp as any, https: okHttps as any, mcPing: okPing as any });
    expect(JSON.stringify(r)).not.toMatch(/eyJ[A-Za-z0-9_-]{10,}/);
  });
});
