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

/**
 * HTTPS reachability. We never send credentials, so ANY HTTP status (incl.
 * 401/403/404) means the host is REACHABLE — these are the normal replies of
 * these API roots to an anonymous GET and must NOT be read as auth failures.
 * Only a transport error (DNS/TCP/TLS/timeout) means unreachable.
 */
export async function httpsProbe(url: string, timeout = 8000): Promise<{ ok: boolean; detail: string; ms: number }> {
  const start = Date.now();
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), timeout);
  try {
    const res = await fetch(url, { method: 'GET', signal: ctrl.signal, redirect: 'manual' });
    const note = res.status === 401 || res.status === 403 || res.status === 404 ? ' (anonym normal)' : '';
    return { ok: true, detail: `HTTP ${res.status}${note}`, ms: Date.now() - start };
  } catch (e: any) {
    const code = e?.cause?.code || e?.code || (e?.name === 'AbortError' ? 'timeout' : '') || e?.message || 'fetch failed';
    return { ok: false, detail: String(code).slice(0, 80), ms: Date.now() - start };
  } finally {
    clearTimeout(t);
  }
}

export interface McPingResult {
  ok: boolean;
  version: string | null;
  protocol: number | null;
  players: string | null;
  detail: string;
  ms: number;
}

/**
 * Minecraft STATUS ping (unauthenticated). Distinguishes a bare TCP connect
 * from a working Minecraft protocol handshake, and reveals the server's real
 * version — exactly what a "TCP ok but handshake timeout" needs.
 */
export async function mcPingProbe(host: string, port: number, timeout = 8000): Promise<McPingResult> {
  const start = Date.now();
  try {
    const mc: any = await import('minecraft-protocol');
    const ping = mc.ping ?? mc.default?.ping;
    const res: any = await Promise.race([
      ping({ host, port }),
      new Promise((_, rej) => setTimeout(() => rej(new Error('ping timeout')), timeout)),
    ]);
    const version = res?.version?.name ?? null;
    const protocol = typeof res?.version?.protocol === 'number' ? res.version.protocol : null;
    const online = res?.players ? `${res.players.online ?? '?'}/${res.players.max ?? '?'}` : null;
    return { ok: true, version, protocol, players: online, detail: `handshake ok, version ${version ?? '?'} (protocol ${protocol ?? '?'})`, ms: Date.now() - start };
  } catch (e: any) {
    const code = e?.cause?.code || e?.code || e?.message || 'ping failed';
    return { ok: false, version: null, protocol: null, players: null, detail: String(code).slice(0, 80), ms: Date.now() - start };
  }
}

export interface Probers {
  tcp?: typeof tcpProbe;
  https?: typeof httpsProbe;
  mcPing?: typeof mcPingProbe;
}

/** Run all probes. Probers are injectable for tests. */
export async function runDiagnostics(probers: Probers = {}): Promise<DiagnosticsResult> {
  const tcp = probers.tcp ?? tcpProbe;
  const https = probers.https ?? httpsProbe;
  const mcPing = probers.mcPing ?? mcPingProbe;
  const start = Date.now();

  const targets: Array<{ name: string; kind: 'tcp' | 'https'; target: string; host: string; port?: number; url?: string }> = [
    { name: 'Minecraft server (TCP)', kind: 'tcp', target: `${config.MC_HOST}:${config.MC_PORT}`, host: config.MC_HOST, port: config.MC_PORT },
    { name: 'Microsoft login', kind: 'https', target: 'login.microsoftonline.com', host: '', url: 'https://login.microsoftonline.com/' },
    { name: 'Xbox Live auth', kind: 'https', target: 'user.auth.xboxlive.com', host: '', url: 'https://user.auth.xboxlive.com/' },
    { name: 'Minecraft services', kind: 'https', target: 'api.minecraftservices.com', host: '', url: 'https://api.minecraftservices.com/' },
    { name: 'Mojang sessionserver (JOIN)', kind: 'https', target: 'sessionserver.mojang.com', host: '', url: 'https://sessionserver.mojang.com/' },
  ];

  const httpProbes: Probe[] = await Promise.all(
    targets.map(async (t) => {
      const r = t.kind === 'tcp' ? await tcp(t.host, t.port!) : await https(t.url!);
      return { name: t.name, target: t.target, ok: r.ok, ms: r.ms, detail: r.detail };
    }),
  );
  // Minecraft status ping = does 25565 actually speak the Java protocol?
  const ping = await mcPing(config.MC_HOST, config.MC_PORT);
  const pingProbe: Probe = {
    name: 'Minecraft handshake (status ping)',
    target: `${config.MC_HOST}:${config.MC_PORT}`,
    ok: ping.ok,
    ms: ping.ms,
    detail: ping.detail,
  };
  const probes: Probe[] = [httpProbes[0], pingProbe, ...httpProbes.slice(1)];

  const session = probes.find((p) => p.target.includes('sessionserver'));
  const server = probes.find((p) => p.name === 'Minecraft server (TCP)');
  const authHosts = probes.filter((p) => p.name !== 'Minecraft server (TCP)' && p.name !== 'Minecraft handshake (status ping)');
  let summary: string;
  if (server && !server.ok) {
    summary = `Der Minecraft-Server ${config.MC_HOST}:${config.MC_PORT} ist nicht per TCP erreichbar — läuft er und stimmen MC_HOST/MC_PORT?`;
  } else if (!ping.ok) {
    summary =
      `TCP zu ${config.MC_HOST}:${config.MC_PORT} ist offen, aber es kommt KEIN Minecraft-Status-Handshake zustande (${ping.detail}). ` +
      'Das deutet darauf hin, dass dieser Port nicht der Java-Entrypoint von Velocity ist, Velocity nicht sauber antwortet, oder eine Versions-/Protokoll-Inkompatibilität vorliegt — NICHT auf ein Auth-Problem.';
  } else if (session && !session.ok) {
    summary = 'Der Join-Schritt braucht sessionserver.mojang.com, und genau der ist von diesem Container NICHT erreichbar (Egress/Firewall).';
  } else if (authHosts.every((p) => p.ok)) {
    summary =
      `Netzwerk ok und der Server antwortet als Minecraft ${ping.version ?? '?'} (Protokoll ${ping.protocol ?? '?'}). ` +
      (ping.version && config.MC_VERSION !== 'auto' && ping.version !== config.MC_VERSION
        ? `ACHTUNG: Server-Version ${ping.version} ≠ MC_VERSION ${config.MC_VERSION} — setze MC_VERSION passend oder auf "auto". `
        : '') +
      'Scheitert der Join trotzdem: Whitelist, Velocity online-mode/forwarding, oder Mojang-Störung.';
  } else {
    summary = 'Mindestens ein Auth-Endpunkt ist blockiert — ausgehende HTTPS-Verbindungen des Containers freigeben.';
  }

  return { probes, summary, tookMs: Date.now() - start };
}
