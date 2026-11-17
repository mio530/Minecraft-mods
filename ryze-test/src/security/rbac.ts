/**
 * Role-based access control. Two independent permission axes are kept
 * separate on purpose (see task spec): OBSERVE (read game state / screenshots)
 * and CONTROL (move, chat, interact). ADMIN is required to approve dangerous
 * actions and manage sessions.
 */
// observe = read state/screenshots; control = move/chat/interact;
// session = start/stop test sessions; admin = approve dangerous ops (owner only).
export type Scope = 'observe' | 'control' | 'session' | 'admin';

export interface Principal {
  kind: 'owner' | 'ai';
  id: string;
  scopes: Scope[];
}

export function owner(id = 'owner'): Principal {
  return { kind: 'owner', id, scopes: ['observe', 'control', 'session', 'admin'] };
}

/**
 * AI principal. By default may observe + control. An AI acting as the primary
 * controller (Claude Code) can also be granted 'session' to start/stop
 * sessions. 'admin' (approving dangerous commands) is NEVER granted to an AI.
 */
export function ai(id: string, scopes: Scope[] = ['observe', 'control']): Principal {
  return { kind: 'ai', id, scopes: scopes.filter((s) => s !== 'admin') };
}

export function can(p: Principal, scope: Scope): boolean {
  return p.scopes.includes(scope);
}

export class AccessError extends Error {
  code = 'forbidden';
  constructor(scope: Scope) {
    super(`Missing required scope: ${scope}`);
  }
}

export function require(p: Principal, scope: Scope): void {
  if (!can(p, scope)) throw new AccessError(scope);
}
