import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);

/** The RyzeSMP target version. Used as the default and as the fallback for any
 *  invalid MC_VERSION so an unknown string (e.g. "auto") never reaches
 *  mineflayer/minecraft-data (which would throw "unsupported protocol version").
 */
export const DEFAULT_MC_VERSION = '1.21.11';

/**
 * True if `v` is a concrete Minecraft Java version that minecraft-data (and
 * therefore mineflayer's protocol) actually supports. Mirrors exactly what
 * mineflayer does internally, so a value we accept here will not be rejected
 * later during the handshake.
 */
export function isSupportedMcVersion(v: string): boolean {
  try {
    const data = require('minecraft-data')(v);
    return !!(data && data.version && data.version.minecraftVersion);
  } catch {
    return false;
  }
}

/**
 * Normalise MC_VERSION to a supported concrete version. Empty/unset/unknown
 * (including the legacy "auto") fall back to DEFAULT_MC_VERSION with a warning.
 * Never returns a string mineflayer can't use.
 */
export function resolveMcVersion(raw: string | undefined | null, warn?: (msg: string) => void): string {
  const v = (raw ?? '').trim();
  if (v === '') return DEFAULT_MC_VERSION;
  if (isSupportedMcVersion(v)) return v;
  warn?.(`MC_VERSION "${v}" is not a supported Minecraft Java version — using ${DEFAULT_MC_VERSION}.`);
  return DEFAULT_MC_VERSION;
}
