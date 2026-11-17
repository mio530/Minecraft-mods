import jwt from 'jsonwebtoken';
import { timingSafeEqual } from 'node:crypto';
import { config } from '../config.js';
import { ai, owner, Principal } from '../security/rbac.js';

/** Constant-time string compare to avoid timing attacks on the password/token. */
export function safeEqual(a: string, b: string): boolean {
  const ab = Buffer.from(a);
  const bb = Buffer.from(b);
  if (ab.length !== bb.length) {
    // Still do a compare to keep timing roughly constant.
    timingSafeEqual(ab, Buffer.from(a));
    return false;
  }
  return timingSafeEqual(ab, bb);
}

export function checkDashboardPassword(pw: string): boolean {
  return safeEqual(pw, config.DASHBOARD_PASSWORD);
}

export function issueOwnerToken(): string {
  return jwt.sign({ kind: 'owner' }, config.JWT_SECRET, { expiresIn: '12h' });
}

export function verifyOwnerToken(token: string): Principal | null {
  try {
    const payload = jwt.verify(token, config.JWT_SECRET) as { kind?: string };
    if (payload.kind === 'owner') return owner();
    return null;
  } catch {
    return null;
  }
}

/** Validates the static bearer token presented by ChatGPT / MCP clients. */
export function verifyMcpToken(token: string | undefined): Principal | null {
  if (!token) return null;
  const bearer = token.startsWith('Bearer ') ? token.slice(7) : token;
  if (safeEqual(bearer, config.MCP_TOKEN)) return ai('chatgpt-mcp');
  return null;
}
