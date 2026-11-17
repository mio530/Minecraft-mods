import Fastify from 'fastify';
import fastifyStatic from '@fastify/static';
import fastifyWebsocket from '@fastify/websocket';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { config } from './config.js';
import { logger } from './logger.js';
import { initAudit } from './security/audit.js';
import { registerRest } from './api/rest.js';
import { registerWs } from './api/ws.js';
import { handleMcpRequest } from './mcp/server.js';
import { verifyMcpToken } from './auth/sessions.js';
import { sessions } from './mc/sessionManager.js';

const __dirname = dirname(fileURLToPath(import.meta.url));

export interface RunningServer {
  url: string;
  close: () => Promise<void>;
}

/**
 * Build and start the HTTP server. Returns the bound URL + a close handle.
 * Exported so tests / tooling can run it in-process; the CLI entry at the
 * bottom calls it when this file is the process entrypoint.
 */
export async function startServer(): Promise<RunningServer> {
  // Fail fast rather than exposing a control plane with default secrets.
  const insecure =
    config.JWT_SECRET.startsWith('CHANGE_ME') ||
    config.MCP_TOKEN.startsWith('CHANGE_ME') ||
    config.DASHBOARD_PASSWORD === 'changeme';
  if (insecure && process.env.ALLOW_INSECURE !== '1') {
    throw new Error(
      'Refusing to start with default secrets. Set JWT_SECRET, MCP_TOKEN and DASHBOARD_PASSWORD ' +
        '(or ALLOW_INSECURE=1 for local dev only).',
    );
  }

  initAudit();

  const app = Fastify({ logger: false, bodyLimit: 1_000_000 });

  // Accept empty JSON bodies (e.g. POST with content-type but no payload)
  // instead of failing with FST_ERR_CTP_EMPTY_JSON_BODY.
  app.removeContentTypeParser('application/json');
  app.addContentTypeParser('application/json', { parseAs: 'string' }, (_req, body, done) => {
    const text = String(body ?? '').trim();
    if (text === '') return done(null, {});
    try {
      done(null, JSON.parse(text));
    } catch {
      const err: Error & { statusCode?: number } = new Error('Invalid JSON body');
      err.statusCode = 400;
      done(err, undefined);
    }
  });

  await app.register(fastifyWebsocket);
  await app.register(fastifyStatic, {
    root: join(__dirname, '..', 'public'),
    prefix: '/',
  });

  // --- MCP endpoint for ChatGPT (bearer-token protected) ---
  for (const method of ['POST', 'GET', 'DELETE'] as const) {
    app.route({
      method,
      url: '/mcp',
      handler: async (request, reply) => {
        const principal = verifyMcpToken(request.headers.authorization);
        if (!principal) {
          return reply.code(401).header('www-authenticate', 'Bearer').send({ error: 'unauthorized' });
        }
        reply.hijack();
        await handleMcpRequest(request.raw, reply.raw, request.body);
      },
    });
  }

  await registerRest(app);
  await registerWs(app);

  app.get('/healthz', async () => ({ ok: true, ts: Date.now() }));

  // Safety net: a background bot/auth error (e.g. a Mojang sessionserver
  // FetchError surfacing as an unhandled rejection) must never take down the
  // HTTP/MCP dashboard. Log a sanitized line and keep serving.
  const onBg = (kind: string) => (err: unknown) => {
    const msg = (err instanceof Error ? err.message : String(err))
      .replace(/eyJ[\w-]{6,}\.[\w-]+\.[\w-]+/g, '[redacted]')
      .replace(/[A-Za-z0-9_-]{40,}/g, '[redacted]')
      .slice(0, 300);
    logger.error({ kind }, `background error (dashboard kept alive): ${msg}`);
  };
  process.on('unhandledRejection', onBg('unhandledRejection'));
  process.on('uncaughtException', onBg('uncaughtException'));

  const address = await app.listen({ port: config.PORT, host: config.HOST });
  logger.info(
    { port: config.PORT, target: `${config.MC_HOST}:${config.MC_PORT}`, auth: config.MC_AUTH },
    'ryze-test listening',
  );
  if (config.JWT_SECRET.startsWith('CHANGE_ME') || config.MCP_TOKEN.startsWith('CHANGE_ME')) {
    logger.warn('Using default secrets — set JWT_SECRET, MCP_TOKEN and DASHBOARD_PASSWORD before production use.');
  }
  return {
    url: address.replace('0.0.0.0', '127.0.0.1'),
    close: async () => {
      await sessions.stopAll();
      await app.close();
    },
  };
}

// Run as a standalone process only when invoked directly.
if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  startServer()
    .then((srv) => {
      const shutdown = async (sig: string) => {
        logger.info({ sig }, 'shutting down');
        await srv.close();
        process.exit(0);
      };
      process.on('SIGINT', () => shutdown('SIGINT'));
      process.on('SIGTERM', () => shutdown('SIGTERM'));
    })
    .catch((err) => {
      logger.error({ err }, 'fatal startup error');
      process.exit(1);
    });
}
