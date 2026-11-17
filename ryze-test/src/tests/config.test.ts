import { describe, expect, it } from 'vitest';
import { resolveHost, resolvePort } from '../config.js';

/**
 * Regression tests for the Pterodactyl startup crash:
 *   Invalid configuration: { PORT: ['Number must be greater than 0'] }
 * caused by Pterodactyl passing PORT="" (empty string) alongside
 * SERVER_PORT=<allocation>.
 */
describe('resolvePort', () => {
  it('uses an explicit valid PORT', () => {
    expect(resolvePort('25572', undefined)).toBe(25572);
  });

  it('EMPTY PORT falls back to SERVER_PORT (the reported bug)', () => {
    expect(resolvePort('', '25572')).toBe(25572);
  });

  it('whitespace-only PORT falls back to SERVER_PORT', () => {
    expect(resolvePort('   ', '25572')).toBe(25572);
  });

  it('MISSING PORT (undefined) falls back to SERVER_PORT', () => {
    expect(resolvePort(undefined, '25572')).toBe(25572);
  });

  it('null PORT falls back to SERVER_PORT', () => {
    expect(resolvePort(null, '25572')).toBe(25572);
  });

  it('empty PORT and empty SERVER_PORT use the dev default', () => {
    expect(resolvePort('', '')).toBe(25571);
    expect(resolvePort(undefined, undefined)).toBe(25571);
  });

  it('never silently falls back to 0 — explicit 0 is a hard error', () => {
    expect(() => resolvePort('0', '25572')).toThrow(/Invalid PORT/);
  });

  it('rejects negative, non-numeric and out-of-range ports', () => {
    expect(() => resolvePort('-5', '25572')).toThrow();
    expect(() => resolvePort('abc', '25572')).toThrow();
    expect(() => resolvePort('70000', undefined)).toThrow();
  });

  it('an invalid SERVER_PORT (when PORT is empty) is a hard error too', () => {
    expect(() => resolvePort('', '0')).toThrow(/SERVER_PORT/);
  });
});

describe('resolveHost', () => {
  it('defaults to 0.0.0.0 (binds all interfaces, not localhost)', () => {
    expect(resolveHost(undefined)).toBe('0.0.0.0');
    expect(resolveHost('')).toBe('0.0.0.0');
    expect(resolveHost('   ')).toBe('0.0.0.0');
  });
  it('keeps an explicit host', () => {
    expect(resolveHost('127.0.0.1')).toBe('127.0.0.1');
    expect(resolveHost('0.0.0.0')).toBe('0.0.0.0');
  });
});
