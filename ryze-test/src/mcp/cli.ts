import { configFromEnv, connectMcp, redact, safeEndpoint } from './client.js';

/**
 * Command-line MCP client for driving the ryze-test server directly.
 * Endpoint + token come from the ENVIRONMENT (never the command line, so they
 * can't leak into shell history or the process list):
 *
 *   export RYZE_MCP_URL="https://<host>/mcp"
 *   export RYZE_MCP_TOKEN="<token>"
 *
 *   node dist/mcp/cli.js diagnose
 *   node dist/mcp/cli.js list
 *   node dist/mcp/cli.js status
 *   node dist/mcp/cli.js call get_player_status '{}'
 *   node dist/mcp/cli.js screenshot out.svg
 */
async function main() {
  const argv = process.argv.slice(2);
  const cmd = argv.shift() ?? 'diagnose';
  const cfg = configFromEnv();

  // diagnose never needs a live connection first; it reports config safely.
  if (cmd === 'diagnose') {
    console.log('MCP endpoint :', cfg.url ? safeEndpoint(cfg.url) : '(RYZE_MCP_URL unset)');
    console.log('MCP token    :', redact(cfg.token));
    if (!cfg.url || !cfg.token) {
      console.log('status       : NOT CONFIGURED — set RYZE_MCP_URL and RYZE_MCP_TOKEN');
      process.exit(2);
    }
    try {
      const mcp = await connectMcp(cfg);
      const tools = await mcp.listTools();
      console.log('status       : CONNECTED');
      console.log('tools        :', tools.join(', '));
      await mcp.close();
    } catch (err) {
      console.log('status       : UNREACHABLE —', err instanceof Error ? err.message : String(err));
      process.exit(1);
    }
    return;
  }

  if (!cfg.url || !cfg.token) {
    console.error('Not configured. Set RYZE_MCP_URL and RYZE_MCP_TOKEN. Run "diagnose" for details.');
    process.exit(2);
  }

  const mcp = await connectMcp(cfg);
  try {
    if (cmd === 'list') {
      console.log((await mcp.listTools()).join('\n'));
    } else if (cmd === 'status') {
      console.log(JSON.stringify(await mcp.call('get_server_status'), null, 2));
    } else if (cmd === 'call') {
      const tool = argv.shift()!;
      const args = argv[0] ? JSON.parse(argv[0]) : {};
      console.log(JSON.stringify(await mcp.call(tool, args), null, 2));
    } else if (cmd === 'screenshot') {
      const out = argv.shift() ?? 'screenshot.svg';
      const res = await mcp.callRaw('get_game_screenshot');
      const img = (res.content ?? []).find((c: any) => c.type === 'image');
      if (!img) {
        console.error('no image returned');
        process.exit(1);
      }
      const { writeFileSync } = await import('node:fs');
      writeFileSync(out, Buffer.from(img.data, 'base64'));
      console.log(`saved ${img.mimeType} -> ${out}`);
    } else {
      console.error('unknown command', cmd);
      process.exit(2);
    }
  } finally {
    await mcp.close();
  }
}

main().catch((err) => {
  console.error(err instanceof Error ? err.message : err);
  process.exit(1);
});
