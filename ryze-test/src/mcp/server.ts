import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { z } from 'zod';
import type { IncomingMessage, ServerResponse } from 'node:http';
import { ai, Scope } from '../security/rbac.js';
import * as svc from '../service.js';
import { config } from '../config.js';
import { logger } from '../logger.js';

/**
 * MCP server exposing the RyzeSMP test tools to any MCP client (Claude Code as
 * the primary controller, and ChatGPT as an optional second client). Every
 * call runs as the AI principal: observe + control, plus session start/stop
 * when MCP_ALLOW_SESSIONS is set. It NEVER gets admin — dangerous commands
 * still require owner approval in the dashboard.
 */

const AI_SCOPES: Scope[] = config.MCP_ALLOW_SESSIONS ? ['observe', 'control', 'session'] : ['observe', 'control'];
const P = ai('mcp-controller', AI_SCOPES);

function text(obj: unknown) {
  return { content: [{ type: 'text' as const, text: typeof obj === 'string' ? obj : JSON.stringify(obj, null, 2) }] };
}

export function buildMcpServer(): McpServer {
  const server = new McpServer(
    { name: 'ryze-test', version: '0.1.0' },
    { capabilities: { tools: {} }, instructions: 'RyzeSMP remote Minecraft test client. Use the observe → act → observe loop: read get_player_status/get_game_screenshot, take ONE action, then observe again and verify before the next action. Dangerous server commands require owner approval and will return approvalRequired=true.' },
  );

  server.tool('get_server_status', 'Service + target server status, active sessions, auth and rate state.', {}, async () => text(svc.getServerStatus(P)));

  server.tool('start_test_session', 'Start a Minecraft test session (connect the bot to RyzeSMP). Returns session id + first observation.', {}, async () => {
    try {
      return text(await svc.startSession(P));
    } catch (e) {
      return text({ error: e instanceof Error ? e.message : String(e), hint: 'If microsoft_login_required, the owner must approve login in the dashboard first.' });
    }
  });

  server.tool('stop_test_session', 'Stop a running test session.', { sessionId: z.string().optional() }, async ({ sessionId }) => text(await svc.stopSession(P, sessionId)));

  server.tool('get_player_status', 'Current player state: position, health, food, dimension, nearby entities, recent chat.', { sessionId: z.string().optional() }, async ({ sessionId }) => text(svc.getPlayerStatus(P, sessionId)));

  server.tool('get_game_screenshot', 'Capture the current game view (schematic radar/HUD, or a real 3D frame if the renderer is enabled).', { sessionId: z.string().optional() }, async ({ sessionId }) => {
    const shot = await svc.getScreenshot(P, sessionId);
    return {
      content: [
        { type: 'image' as const, data: shot.base64, mimeType: shot.mime },
        { type: 'text' as const, text: `screenshot kind=${shot.kind} (${shot.width}x${shot.height}). ${shot.note}` },
      ],
    };
  });

  server.tool('inspect_inventory', 'List the items currently in the player inventory (read-only).', { sessionId: z.string().optional() }, async ({ sessionId }) => text(svc.inspectInventory(P, sessionId)));

  server.tool(
    'send_chat_message',
    'Send a chat message or command. Plain text is sent directly; commands starting with / that are marked dangerous return approvalRequired=true until the owner approves.',
    { message: z.string().min(1).max(256), sessionId: z.string().optional() },
    async ({ message, sessionId }) => text(await svc.sendChatMessage(P, message, sessionId)),
  );

  server.tool(
    'execute_player_action',
    'Perform one player action, then returns observations before AND after so you can verify the result. type: move|look|lookAtNearest|attackNearest|useItem|jumpOnce|stopAll.',
    {
      type: z.enum(['move', 'look', 'lookAtNearest', 'attackNearest', 'useItem', 'jumpOnce', 'stopAll']),
      direction: z.enum(['forward', 'back', 'left', 'right']).optional(),
      durationMs: z.number().int().min(50).max(5000).optional(),
      sprint: z.boolean().optional(),
      sneak: z.boolean().optional(),
      jump: z.boolean().optional(),
      yawDeg: z.number().optional(),
      pitchDeg: z.number().optional(),
      entityType: z.enum(['player', 'mob', 'any']).optional(),
      sessionId: z.string().optional(),
    },
    async ({ sessionId, ...action }) => text(await svc.executePlayerAction(P, action, sessionId)),
  );

  server.tool('get_test_results', 'List available predefined tests and recent recorded test results.', {}, async () => text(svc.getTests(P)));

  server.tool('run_gameplay_test', 'Run a predefined gameplay test by name (or "all"). Returns pass/fail with expected vs actual.', { name: z.string(), sessionId: z.string().optional() }, async ({ name, sessionId }) => text(await svc.runTestByName(P, name, sessionId)));

  return server;
}

/**
 * Handle one MCP HTTP request in stateless mode. Fastify delegates the raw
 * Node req/res here after it has validated the bearer token.
 */
export async function handleMcpRequest(req: IncomingMessage, res: ServerResponse, body: unknown): Promise<void> {
  const server = buildMcpServer();
  const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined });
  res.on('close', () => {
    transport.close();
    server.close();
  });
  try {
    await server.connect(transport);
    await transport.handleRequest(req, res, body);
  } catch (err) {
    logger.error({ err }, 'MCP request failed');
    if (!res.headersSent) {
      res.writeHead(500, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ jsonrpc: '2.0', error: { code: -32603, message: 'internal error' }, id: null }));
    }
  }
}
