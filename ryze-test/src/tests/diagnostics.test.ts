import { describe, expect, it } from 'vitest';

process.env.DB_PATH = '/tmp/ryze-diag-test.db';
process.env.MC_HOST = '203.0.113.10';
process.env.MC_PORT = '25565';
process.env.JWT_SECRET = 'diag_test_secret_123456';
process.env.MCP_TOKEN = 'diag-mcp-token';
process.env.DASHBOARD_PASSWORD = 'pw123456';

const okTcp = async () => ({ ok: true, detail: 'TCP connect ok', ms: 5 });
const okHttps = async (url: string) => ({ ok: true, detail: 'HTTP 200', ms: 5 });

describe('runDiagnostics', () => {
  it('probes the MC server plus all auth/join hosts', async () => {
    const { runDiagnostics } = await import('../mc/diagnostics.js');
    const r = await runDiagnostics({ tcp: okTcp as any, https: okHttps as any });
    const targets = r.probes.map((p) => p.target);
    expect(targets).toContain('203.0.113.10:25565');
    expect(targets.some((t) => t.includes('sessionserver.mojang.com'))).toBe(true);
    expect(targets.some((t) => t.includes('api.minecraftservices.com'))).toBe(true);
    expect(r.probes.every((p) => p.ok)).toBe(true);
    expect(r.summary).toMatch(/erreichbar/i);
  });

  it('pins the failure on sessionserver when only the join host is blocked', async () => {
    const { runDiagnostics } = await import('../mc/diagnostics.js');
    const https = async (url: string) =>
      url.includes('sessionserver.mojang.com')
        ? { ok: false, detail: 'ERR_STREAM_PREMATURE_CLOSE', ms: 10 }
        : { ok: true, detail: 'HTTP 200', ms: 5 };
    const r = await runDiagnostics({ tcp: okTcp as any, https: https as any });
    const session = r.probes.find((p) => p.target.includes('sessionserver'))!;
    expect(session.ok).toBe(false);
    expect(r.summary).toMatch(/sessionserver\.mojang\.com/);
    expect(r.summary).toMatch(/Egress|Firewall|erreichbar/);
  });

  it('reports an unreachable Minecraft server distinctly', async () => {
    const { runDiagnostics } = await import('../mc/diagnostics.js');
    const tcp = async () => ({ ok: false, detail: 'ECONNREFUSED', ms: 3 });
    const r = await runDiagnostics({ tcp: tcp as any, https: okHttps as any });
    expect(r.summary).toMatch(/nicht per TCP erreichbar|MC_HOST/);
  });

  it('never includes anything token-shaped', async () => {
    const { runDiagnostics } = await import('../mc/diagnostics.js');
    const r = await runDiagnostics({ tcp: okTcp as any, https: okHttps as any });
    expect(JSON.stringify(r)).not.toMatch(/eyJ[A-Za-z0-9_-]{10,}/);
  });
});
