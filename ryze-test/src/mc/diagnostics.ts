import net from 'node:net';
import { config } from '../config.js';

/**
 * Outbound connectivity diagnostics: tells whether the container can reach the
 * endpoints the Minecraft login/join needs, so a failure can be pinned on
 * network/egress vs Mojang vs the game server — without exposing any secret.
 */
export interface Probe {
  name: string;
  target: string;
  ok: boolean;
  ms: number;
  detail: string;
}

export interface DiagnosticsResult {
  probes: Probe[];
  summary: string;
  tookMs: number;
}

/** TCP reachability (used for the Minecraft server / Velocity proxy). */
export function tcpProbe(host: string, port: number, timeout = 6000): Promise<{ ok: boolean; detail: string; ms: number }> {
  const start = Date.now();
  return new Promise((resolve) => {
    const sock = net.connect({ host, port });
    let done = false;
    const finish = (ok: boolean, detail: string) => {
      if (done) return;
      done = true;
      try {
        sock.destroy();
      } catch {
        /* ignore */
      }
      resolve({ ok, detail, ms: Date.now() - start });
    };
    sock.setTimeout(timeout);
    sock.on('connect', () => finish(true, 'TCP connect ok'));
    sock.on('timeout', () => finish(false, 'timeout'));
    sock.on('error', (e: any) => finish(false, e?.code || e?.message || 'error'));
  });
}

/** HTTPS reachability (does not send credentials; any HTTP status = reachable). */
export async function httpsProbe(url: string, timeout = 8000): Promise<{ ok: boolean; detail: string; ms: number }> {
  const start = Date.now();
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), timeout);
  try {
    const res = await fetch(url, { method: 'GET', signal: ctrl.signal, redirect: 'manual' });
    return { ok: true, detail: `HTTP ${res.status}`, ms: Date.now() - start };
  } catch (e: any) {
    const code = e?.cause?.code || e?.code || (e?.name === 'AbortError' ? 'timeout' : '') || e?.message || 'fetch failed';
    return { ok: false, detail: String(code).slice(0, 80), ms: Date.now() - start };
  }
}

export interface Probers {
  tcp?: typeof tcpProbe;
  https?: typeof httpsProbe;
}

/** Run all probes. Probers are injectable for tests. */
export async function runDiagnostics(probers: Probers = {}): Promise<DiagnosticsResult> {
  const tcp = probers.tcp ?? tcpProbe;
  const https = probers.https ?? httpsProbe;
  const start = Date.now();

  const targets: Array<{ name: string; kind: 'tcp' | 'https'; target: string; host: string; port?: number; url?: string }> = [
    { name: 'Minecraft server (TCP)', kind: 'tcp', target: `${config.MC_HOST}:${config.MC_PORT}`, host: config.MC_HOST, port: config.MC_PORT },
    { name: 'Microsoft login', kind: 'https', target: 'login.microsoftonline.com', host: '', url: 'https://login.microsoftonline.com/' },
    { name: 'Xbox Live auth', kind: 'https', target: 'user.auth.xboxlive.com', host: '', url: 'https://user.auth.xboxlive.com/' },
    { name: 'Minecraft services', kind: 'https', target: 'api.minecraftservices.com', host: '', url: 'https://api.minecraftservices.com/' },
    { name: 'Mojang sessionserver (JOIN)', kind: 'https', target: 'sessionserver.mojang.com', host: '', url: 'https://sessionserver.mojang.com/' },
  ];

  const probes: Probe[] = await Promise.all(
    targets.map(async (t) => {
      const r = t.kind === 'tcp' ? await tcp(t.host, t.port!) : await https(t.url!);
      return { name: t.name, target: t.target, ok: r.ok, ms: r.ms, detail: r.detail };
    }),
  );

  const session = probes.find((p) => p.target.includes('sessionserver'));
  const server = probes.find((p) => p.name.startsWith('Minecraft server'));
  const authHosts = probes.filter((p) => p.name !== 'Minecraft server (TCP)');
  let summary: string;
  if (session && !session.ok) {
    summary = 'Der Join-Schritt braucht sessionserver.mojang.com, und genau der ist von diesem Container NICHT erreichbar (Egress/Firewall). Das erklärt den Login-Abbruch.';
  } else if (authHosts.every((p) => p.ok) && server && server.ok) {
    summary = 'Alle benötigten Endpunkte erreichbar. Falls der Join trotzdem scheitert: Whitelist/online-mode/Velocity-Konfiguration prüfen, oder Mojang-Störung.';
  } else if (server && !server.ok) {
    summary = `Der Minecraft-Server ${config.MC_HOST}:${config.MC_PORT} ist nicht per TCP erreichbar — läuft er und stimmen MC_HOST/MC_PORT?`;
  } else {
    summary = 'Mindestens ein Auth-Endpunkt ist blockiert — ausgehende HTTPS-Verbindungen des Containers einschränkungsfrei machen.';
  }

  return { probes, summary, tookMs: Date.now() - start };
}
