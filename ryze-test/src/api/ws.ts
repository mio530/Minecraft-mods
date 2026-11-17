import type { FastifyInstance } from 'fastify';
import { verifyOwnerToken } from '../auth/sessions.js';
import { sessions } from '../mc/sessionManager.js';
import { schematicSvg } from '../mc/viewer.js';
import { Observation } from '../mc/types.js';
import { logger } from '../logger.js';

/**
 * Live WebSocket channel for the dashboard:
 *   server → client: { type:'observation', obs, svg } every tick, plus
 *                    approval events.
 *   client → server: live human control (keyboard/touch) and look deltas.
 *
 * Authenticated with the owner JWT passed as ?token=. Control messages require
 * the owner principal (full control); observers could be added similarly.
 */
export async function registerWs(app: FastifyInstance): Promise<void> {
  app.get('/ws', { websocket: true }, (socket, req) => {
    const url = new URL(req.url ?? '', 'http://localhost');
    const token = url.searchParams.get('token') ?? '';
    const principal = verifyOwnerToken(token);
    if (!principal) {
      socket.close(1008, 'unauthorized');
      return;
    }

    const send = (obj: unknown) => {
      if (socket.readyState === socket.OPEN) socket.send(JSON.stringify(obj));
    };

    const onObs = (obs: Observation) => send({ type: 'observation', obs, svg: schematicSvg(obs) });
    const onApprovalReq = (a: unknown) => send({ type: 'approval_requested', approval: a });
    const onApprovalRes = (a: unknown) => send({ type: 'approval_resolved', approval: a });
    const onBotError = (e: unknown) => send({ type: 'botError', error: e, ts: Date.now() });

    sessions.on('observation', onObs);
    sessions.on('approval_requested', onApprovalReq);
    sessions.on('approval_resolved', onApprovalRes);
    sessions.on('bot_error', onBotError);

    // send an immediate snapshot if a session exists
    const cur = sessions.current();
    if (cur?.bot.connected) onObs(cur.bot.observe());

    socket.on('message', (raw: Buffer) => {
      let msg: any;
      try {
        msg = JSON.parse(raw.toString());
      } catch {
        return;
      }
      try {
        const bot = sessions.current()?.bot;
        if (!bot?.connected) return;
        switch (msg.type) {
          case 'control': // { type:'control', key, state }
            bot.setControl(msg.key, !!msg.state);
            break;
          case 'look': // { type:'look', dYaw, dPitch }
            bot.lookDelta(Number(msg.dYaw) || 0, Number(msg.dPitch) || 0);
            break;
          case 'ping':
            send({ type: 'pong' });
            break;
        }
      } catch (err) {
        logger.warn({ err }, 'ws control error');
      }
    });

    socket.on('close', () => {
      sessions.off('observation', onObs);
      sessions.off('approval_requested', onApprovalReq);
      sessions.off('approval_resolved', onApprovalRes);
      sessions.off('bot_error', onBotError);
    });
  });
}
