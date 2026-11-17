import { config } from '../config.js';

/**
 * Simple in-memory sliding-window rate limiter, keyed by an arbitrary id
 * (session id, token, ip). Keeps the AI and any client from flooding the
 * live server with actions.
 */
const windows = new Map<string, number[]>();

export function allow(key: string, perMin = config.ACTION_RATE_PER_MIN): boolean {
  const now = Date.now();
  const cutoff = now - 60_000;
  const arr = (windows.get(key) ?? []).filter((t) => t > cutoff);
  if (arr.length >= perMin) {
    windows.set(key, arr);
    return false;
  }
  arr.push(now);
  windows.set(key, arr);
  return true;
}

export function remaining(key: string, perMin = config.ACTION_RATE_PER_MIN): number {
  const cutoff = Date.now() - 60_000;
  const arr = (windows.get(key) ?? []).filter((t) => t > cutoff);
  return Math.max(0, perMin - arr.length);
}
