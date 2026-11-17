/**
 * Role-based access control. Two independent permission axes are kept
 * separate on purpose (see task spec): OBSERVE (read game state / screenshots)
 * and CONTROL (move, chat, interact). ADMIN is required to approve dangerous
 * actions and manage sessions.
 */
export type Scope = 'observe' | 'control' | 'admin';

export interface Principal {
  kind: 'owner' | 'ai';
  id: string;
  scopes: Scope[];
}

export function owner(id = 'owner'): Principal {
  return { kind: 'owner', id, scopes: ['observe', 'control', 'admin'] };
}

/** Default AI principal: may observe and control, but NOT approve dangerous ops. */
export function ai(id: string, scopes: Scope[] = ['observe', 'control']): Principal {
  return { kind: 'ai', id, scopes };
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
