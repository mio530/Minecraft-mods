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

async function main() {
  initAudit();

  const app = Fastify({ logger: false, bodyLimit: 1_000_000 });

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

  await app.listen({ port: config.PORT, host: config.HOST });
  logger.info(
    { port: config.PORT, target: `${config.MC_HOST}:${config.MC_PORT}`, auth: config.MC_AUTH },
    'ryze-test listening',
  );
  if (config.JWT_SECRET.startsWith('CHANGE_ME') || config.MCP_TOKEN.startsWith('CHANGE_ME')) {
    logger.warn('Using default secrets — set JWT_SECRET, MCP_TOKEN and DASHBOARD_PASSWORD before production use.');
  }

  const shutdown = async (sig: string) => {
    logger.info({ sig }, 'shutting down');
    await sessions.stopAll();
    await app.close();
    process.exit(0);
  };
  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('SIGTERM', () => shutdown('SIGTERM'));
}

main().catch((err) => {
  logger.error({ err }, 'fatal startup error');
  process.exit(1);
});
