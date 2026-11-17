import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';

/**
 * A real MCP client so Claude Code (or any MCP client) can connect to the
 * ryze-test MCP server over HTTP and call its tools — the same transport
 * ChatGPT's connector uses. This is the "Claude Code is the controller" path.
 */
export interface RyzeMcp {
  client: Client;
  listTools: () => Promise<string[]>;
  call: <T = unknown>(name: string, args?: Record<string, unknown>) => Promise<T>;
  callRaw: (name: string, args?: Record<string, unknown>) => Promise<any>;
  close: () => Promise<void>;
}

export async function connectMcp(baseUrl: string, token: string): Promise<RyzeMcp> {
  const transport = new StreamableHTTPClientTransport(new URL(baseUrl), {
    requestInit: { headers: { authorization: `Bearer ${token}` } },
  });
  const client = new Client({ name: 'claude-code', version: '1.0.0' }, { capabilities: {} });
  await client.connect(transport);

  const callRaw = async (name: string, args: Record<string, unknown> = {}) =>
    client.callTool({ name, arguments: args });

  const call = async <T = unknown>(name: string, args: Record<string, unknown> = {}): Promise<T> => {
    const res: any = await callRaw(name, args);
    const textPart = (res.content ?? []).find((c: any) => c.type === 'text');
    const text = textPart?.text ?? '';
    try {
      return JSON.parse(text) as T;
    } catch {
      return text as unknown as T;
    }
  };

  return {
    client,
    listTools: async () => (await client.listTools()).tools.map((t) => t.name),
    call,
    callRaw,
    close: async () => {
      await client.close();
      await transport.close();
    },
  };
}
