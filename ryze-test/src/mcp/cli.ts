import { connectMcp } from './client.js';

/**
 * Command-line MCP client for driving the ryze-test server directly.
 *
 *   node dist/mcp/cli.js <baseUrl> <token> list
 *   node dist/mcp/cli.js <baseUrl> <token> call get_player_status '{}'
 *   node dist/mcp/cli.js <baseUrl> <token> screenshot out.svg
 *
 * Or set MCP_URL and MCP_TOKEN env vars and omit the first two args.
 */
async function main() {
  const argv = process.argv.slice(2);
  let baseUrl = process.env.MCP_URL ?? '';
  let token = process.env.MCP_TOKEN ?? '';
  // allow positional url/token if they look like one
  if (argv[0]?.startsWith('http')) baseUrl = argv.shift()!;
  if (argv[0] && !['list', 'call', 'screenshot', 'status'].includes(argv[0])) token = argv.shift()!;
  const cmd = argv.shift() ?? 'list';

  if (!baseUrl || !token) {
    console.error('Usage: cli <baseUrl> <token> <list|call|screenshot|status> [...]');
    process.exit(2);
  }

  const mcp = await connectMcp(baseUrl, token);
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
        console.error('no image returned:', JSON.stringify(res));
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
