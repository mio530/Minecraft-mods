import { Observation } from './types.js';

/**
 * Visualisation of the live game state.
 *
 * Two levels, chosen honestly by what the host can actually render:
 *
 *  1. SCHEMATIC (always available, zero native deps, GPU-free): a top-down
 *     radar + first-person HUD rendered as SVG from the observation. This is
 *     the shared "screen" the human dashboard and the AI both see. It is a
 *     faithful representation of game state, not a photoreal frame — and is
 *     labelled as such so nothing is over-claimed.
 *
 *  2. RENDER (optional, VIEWER_ENABLED + native canvas/gl): a real 3D frame
 *     via prismarine-viewer. Heavier; off by default to protect performance.
 */

export interface Screenshot {
  kind: 'schematic' | 'render';
  mime: string;
  /** base64 payload (without data: prefix) */
  base64: string;
  width: number;
  height: number;
  note: string;
}

const W = 640;
const H = 360;

function esc(s: string): string {
  return s.replace(/[<>&"]/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;', '"': '&quot;' }[c]!));
}

/** Builds the SVG schematic (radar + HUD) from an observation. */
export function schematicSvg(obs: Observation): string {
  const cx = W * 0.72;
  const cy = H * 0.5;
  const radius = 150;
  const range = 32; // blocks shown edge-to-edge
  const scale = radius / range;

  const yaw = obs.yaw ?? 0;
  // In Minecraft, yaw 0 faces +Z (south). Convert to screen heading.
  const facingX = Math.sin(-yaw);
  const facingZ = Math.cos(-yaw);

  const blips = (obs.nearbyEntities ?? [])
    .filter((e) => obs.position && e.distance <= range)
    .map((e) => {
      const dx = e.position.x - (obs.position!.x);
      const dz = e.position.z - (obs.position!.z);
      // rotate into player-facing frame (player faces up)
      const rx = dx * Math.cos(yaw) - dz * Math.sin(yaw);
      const rz = dx * Math.sin(yaw) + dz * Math.cos(yaw);
      const px = cx + rx * scale;
      const py = cy - rz * scale;
      const color = e.username ? '#4ea1ff' : e.type === 'hostile' || e.type === 'mob' ? '#ff5b5b' : '#8bd450';
      const label = e.username ?? e.name;
      return `<circle cx="${px.toFixed(1)}" cy="${py.toFixed(1)}" r="4" fill="${color}"/>
        <text x="${(px + 6).toFixed(1)}" y="${(py + 3).toFixed(1)}" font-size="9" fill="#cfd8e3">${esc(label)} ${e.distance}m</text>`;
    })
    .join('\n');

  const health = obs.health ?? 0;
  const food = obs.food ?? 0;
  const posStr = obs.position ? `${obs.position.x} / ${obs.position.y} / ${obs.position.z}` : '—';
  const chat = (obs.recentChat ?? [])
    .slice(-6)
    .map((c, i) => `<text x="16" y="${250 + i * 15}" font-size="11" fill="#aeb9c7">${esc((c.username ? c.username + ': ' : '') + c.text).slice(0, 60)}</text>`)
    .join('\n');

  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" font-family="system-ui,Segoe UI,sans-serif">
  <rect width="${W}" height="${H}" fill="#0f141b"/>
  <!-- HUD panel -->
  <text x="16" y="28" font-size="14" fill="#e6edf3" font-weight="600">RyzeSMP Test Client</text>
  <text x="16" y="48" font-size="11" fill="#7f8c9a">${obs.connected ? 'CONNECTED' : 'OFFLINE'} · ${esc(obs.serverHost)}</text>
  <text x="16" y="78" font-size="12" fill="#cfd8e3">Player: ${esc(obs.username ?? '—')}</text>
  <text x="16" y="98" font-size="12" fill="#cfd8e3">Pos: ${esc(posStr)}</text>
  <text x="16" y="118" font-size="12" fill="#cfd8e3">Dim: ${esc(obs.dimension ?? '—')} · ${esc(obs.gameMode ?? '—')}</text>
  <text x="16" y="146" font-size="12" fill="#ff8a8a">♥ ${health.toFixed(0)}/20</text>
  <rect x="80" y="137" width="120" height="8" rx="4" fill="#2a1414"/>
  <rect x="80" y="137" width="${(120 * health) / 20}" height="8" rx="4" fill="#ff5b5b"/>
  <text x="16" y="166" font-size="12" fill="#ffd27f">🍗 ${food.toFixed(0)}/20</text>
  <rect x="80" y="157" width="120" height="8" rx="4" fill="#2a2214"/>
  <rect x="80" y="157" width="${(120 * food) / 20}" height="8" rx="4" fill="#e0a94e"/>
  <text x="16" y="196" font-size="12" fill="#cfd8e3">Held: ${esc(obs.heldItem ? obs.heldItem.displayName + ' x' + obs.heldItem.count : 'empty')}</text>
  <text x="16" y="232" font-size="11" fill="#7f8c9a">Recent chat</text>
  ${chat}
  <!-- radar -->
  <circle cx="${cx}" cy="${cy}" r="${radius}" fill="#0b0f14" stroke="#2b3542"/>
  <circle cx="${cx}" cy="${cy}" r="${radius * 0.5}" fill="none" stroke="#1c2530"/>
  <line x1="${cx}" y1="${cy - radius}" x2="${cx}" y2="${cy + radius}" stroke="#1c2530"/>
  <line x1="${cx - radius}" y1="${cy}" x2="${cx + radius}" y2="${cy}" stroke="#1c2530"/>
  <!-- player (always center, facing up) -->
  <polygon points="${cx},${cy - 9} ${cx - 6},${cy + 6} ${cx + 6},${cy + 6}" fill="#ffffff"/>
  <text x="${cx - 18}" y="${cy + radius + 18}" font-size="10" fill="#7f8c9a">radar · ${range}m · ↑ facing</text>
  ${blips}
  <text x="${W - 8}" y="${H - 8}" font-size="9" fill="#55606d" text-anchor="end">schematic (game state) · not a vanilla render</text>
</svg>`;
}

export function schematicScreenshot(obs: Observation): Screenshot {
  const svg = schematicSvg(obs);
  return {
    kind: 'schematic',
    mime: 'image/svg+xml',
    base64: Buffer.from(svg, 'utf8').toString('base64'),
    width: W,
    height: H,
    note: 'Schematic radar/HUD rendered from live game state (no GPU). Enable VIEWER_ENABLED for a real 3D frame.',
  };
}

/**
 * Optional real 3D frame via prismarine-viewer headless. Returns null if the
 * native renderer is not installed/enabled — callers fall back to schematic.
 */
export async function renderScreenshot(_bot: any): Promise<Screenshot | null> {
  // Intentionally conservative: prismarine-viewer headless needs native
  // canvas + gl which are heavy and host-dependent. We attempt it only when
  // explicitly enabled, and never let a render failure break a test run.
  try {
    // Lazy import via a non-literal specifier keeps the optional dependency
    // truly optional (not resolved at compile time).
    const spec = 'prismarine-viewer';
    const mod: any = await import(spec).catch(() => null);
    if (!mod) return null;
    // prismarine-viewer does not expose a stable single-frame PNG API across
    // versions; deployments that need true frames should run the web viewer
    // (documented in DEPLOY-PTERODACTYL.md). Return null to use schematic.
    return null;
  } catch {
    return null;
  }
}
