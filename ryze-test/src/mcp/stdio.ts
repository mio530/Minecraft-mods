import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { buildMcpServer } from './server.js';
import { initAudit } from '../security/audit.js';
import { logger } from '../logger.js';

/**
 * Run the ryze-test MCP server over **stdio**, the standard transport for a
 * local MCP server spawned by an MCP host (e.g. Claude Code via `.mcp.json`).
 *
 * Note: a stdio server runs wherever the host spawns it. To drive a bot that
 * actually joins RyzeSMP, point Claude Code at the REMOTE HTTP endpoint on
 * Pterodactyl instead (the bot must run where RyzeSMP is reachable). This
 * stdio entry is for local use and for hosts that only support stdio.
 *
 * stdout is reserved for the MCP protocol; all logging goes to stderr.
 */
async function main() {
  initAudit();
  const server = buildMcpServer();
  const transport = new StdioServerTransport();
  await server.connect(transport);
  logger.info('ryze-test MCP stdio server ready');
}

main().catch((err) => {
  logger.error({ err }, 'stdio server failed');
  process.exit(1);
});
