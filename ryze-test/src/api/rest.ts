import type { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { checkDashboardPassword, issueOwnerToken, verifyOwnerToken } from '../auth/sessions.js';
import { startLogin, logout as msLogout, status as msStatus } from '../auth/microsoft.js';
import { Principal } from '../security/rbac.js';
import { recentAudit } from '../security/audit.js';
import { sessions } from '../mc/sessionManager.js';
import * as svc from '../service.js';

/** Extracts the owner principal from Authorization: Bearer <jwt>. */
function principalFrom(req: FastifyRequest): Principal | null {
  const auth = req.headers.authorization;
  if (!auth) return null;
  const token = auth.startsWith('Bearer ') ? auth.slice(7) : auth;
  return verifyOwnerToken(token);
}

function requireOwner(req: FastifyRequest, reply: FastifyReply): Principal | null {
  const p = principalFrom(req);
  if (!p) {
    reply.code(401).send({ error: 'unauthorized' });
    return null;
  }
  return p;
}

export async function registerRest(app: FastifyInstance): Promise<void> {
  // --- dashboard login ---
  app.post('/api/login', async (req, reply) => {
    const { password } = (req.body ?? {}) as { password?: string };
    if (!password || !checkDashboardPassword(password)) {
      return reply.code(401).send({ error: 'invalid password' });
    }
    return { token: issueOwnerToken() };
  });

  // --- Microsoft auth (owner-driven device-code flow) ---
  app.get('/api/auth/status', async (req, reply) => {
    if (!requireOwner(req, reply)) return;
    return msStatus();
  });
  app.post('/api/auth/login', async (req, reply) => {
    if (!requireOwner(req, reply)) return;
    // Starts the device-code flow in the background; the dashboard polls
    // /api/auth/status for the code and completion. Never returns tokens.
    const r = startLogin();
    return { ...r, status: msStatus() };
  });
  app.post('/api/auth/logout', async (req, reply) => {
    if (!requireOwner(req, reply)) return;
    msLogout();
    return { ok: true };
  });

  // --- status / observation ---
  app.get('/api/server', async (req, reply) => {
    const p = requireOwner(req, reply);
    if (!p) return;
    return svc.getServerStatus(p);
  });
  app.get('/api/player', async (req, reply) => {
    const p = requireOwner(req, reply);
    if (!p) return;
    try {
      return svc.getPlayerStatus(p);
    } catch (e) {
      return reply.code(409).send({ error: e instanceof Error ? e.message : String(e) });
    }
  });
  app.get('/api/inventory', async (req, reply) => {
    const p = requireOwner(req, reply);
    if (!p) return;
    try {
      return svc.inspectInventory(p);
    } catch (e) {
      return reply.code(409).send({ error: e instanceof Error ? e.message : String(e) });
    }
  });
  app.get('/api/screenshot', async (req, reply) => {
    const p = requireOwner(req, reply);
    if (!p) return;
    try {
      const shot = await svc.getScreenshot(p);
      reply.header('content-type', shot.mime);
      reply.header('cache-control', 'no-store');
      return reply.send(Buffer.from(shot.base64, 'base64'));
    } catch (e) {
      return reply.code(409).send({ error: e instanceof Error ? e.message : String(e) });
    }
  });

  // --- control ---
  app.post('/api/session/start', async (req, reply) => {
    const p = requireOwner(req, reply);
    if (!p) return;
    try {
      return await svc.startSession(p);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      return reply.code(msg === 'microsoft_login_required' ? 428 : 409).send({ error: msg });
    }
  });
  app.post('/api/session/stop', async (req, reply) => {
    const p = requireOwner(req, reply);
    if (!p) return;
    const { id } = (req.body ?? {}) as { id?: string };
    return svc.stopSession(p, id);
  });
  app.post('/api/chat', async (req, reply) => {
    const p = requireOwner(req, reply);
    if (!p) return;
    const { message } = (req.body ?? {}) as { message?: string };
    try {
      return await svc.sendChatMessage(p, String(message ?? ''));
    } catch (e) {
      return reply.code(400).send({ error: e instanceof Error ? e.message : String(e) });
    }
  });
  app.post('/api/action', async (req, reply) => {
    const p = requireOwner(req, reply);
    if (!p) return;
    try {
      return await svc.executePlayerAction(p, (req.body ?? {}) as any);
    } catch (e) {
      return reply.code(400).send({ error: e instanceof Error ? e.message : String(e) });
    }
  });

  // --- tests ---
  app.get('/api/tests', async (req, reply) => {
    const p = requireOwner(req, reply);
    if (!p) return;
    return svc.getTests(p);
  });
  app.post('/api/tests/run', async (req, reply) => {
    const p = requireOwner(req, reply);
    if (!p) return;
    const { name } = (req.body ?? {}) as { name?: string };
    try {
      return await svc.runTestByName(p, String(name ?? 'all'));
    } catch (e) {
      return reply.code(409).send({ error: e instanceof Error ? e.message : String(e) });
    }
  });

  // --- audit + approvals ---
  app.get('/api/audit', async (req, reply) => {
    if (!requireOwner(req, reply)) return;
    return recentAudit(200);
  });
  app.get('/api/approvals', async (req, reply) => {
    if (!requireOwner(req, reply)) return;
    return sessions.pendingApprovals();
  });
  app.post('/api/approvals/resolve', async (req, reply) => {
    const p = requireOwner(req, reply);
    if (!p) return;
    const { id, approve } = (req.body ?? {}) as { id?: string; approve?: boolean };
    const ok = await sessions.resolveApproval(String(id), !!approve, p.id);
    return { ok };
  });
}
