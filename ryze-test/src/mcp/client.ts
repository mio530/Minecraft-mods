import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';

/**
 * A real MCP client (official @modelcontextprotocol/sdk) so Claude Code — the
 * PRIMARY controller — can connect to the ryze-test MCP server over HTTP and
 * call its tools. Same StreamableHTTP transport the server implements.
 *
 * Config comes from the environment; tokens are never printed. Use redact()
 * for any diagnostics.
 */

export interface RyzeMcp {
  client: Client;
  listTools: () => Promise<string[]>;
  call: <T = unknown>(name: string, args?: Record<string, unknown>) => Promise<T>;
  callRaw: (name: string, args?: Record<string, unknown>) => Promise<any>;
  close: () => Promise<void>;
}

export interface McpConfig {
  url: string;
  token: string;
  retries?: number;
}

/** Read endpoint + token from env. Supports a couple of common names. */
export function configFromEnv(): McpConfig {
  const url = process.env.RYZE_MCP_URL ?? process.env.MCP_URL ?? '';
  const token = process.env.RYZE_MCP_TOKEN ?? process.env.MCP_TOKEN ?? '';
  return { url, token };
}

/** Mask a secret so it can appear in diagnostics without being revealed. */
export function redact(secret: string): string {
  if (!secret) return '(unset)';
  if (secret.length <= 8) return '****';
  return `${secret.slice(0, 3)}…${secret.slice(-2)} (len ${secret.length})`;
}

/** Safe host:port for diagnostics (no path, no query, no credentials). */
export function safeEndpoint(url: string): string {
  try {
    const u = new URL(url);
    return `${u.protocol}//${u.host}${u.pathname}`;
  } catch {
    return '(invalid url)';
  }
}

function makeTransport(cfg: McpConfig): StreamableHTTPClientTransport {
  return new StreamableHTTPClientTransport(new URL(cfg.url), {
    requestInit: { headers: { authorization: `Bearer ${cfg.token}` } },
  });
}

const delay = (ms: number) => new Promise((r) => setTimeout(r, ms));

/**
 * Connect with bounded retries + exponential backoff. Throws a redacted error
 * on final failure (never leaks the token).
 */
export async function connectMcp(urlOrCfg: string | McpConfig, maybeToken?: string): Promise<RyzeMcp> {
  const cfg: McpConfig = typeof urlOrCfg === 'string' ? { url: urlOrCfg, token: maybeToken ?? '' } : urlOrCfg;
  if (!cfg.url) throw new Error('MCP url not set (RYZE_MCP_URL)');
  if (!cfg.token) throw new Error('MCP token not set (RYZE_MCP_TOKEN)');

  const retries = cfg.retries ?? 3;
  let transport!: StreamableHTTPClientTransport;
  let client!: Client;
  let lastErr: unknown;

  for (let attempt = 1; attempt <= retries; attempt++) {
    try {
      transport = makeTransport(cfg);
      client = new Client({ name: 'claude-code', version: '1.0.0' }, { capabilities: {} });
      await client.connect(transport);
      lastErr = undefined;
      break;
    } catch (err) {
      lastErr = err;
      try {
        await transport?.close();
      } catch {
        /* ignore */
      }
      if (attempt < retries) await delay(500 * 2 ** (attempt - 1));
    }
  }
  if (lastErr) {
    const msg = lastErr instanceof Error ? lastErr.message : String(lastErr);
    throw new Error(`MCP connect failed after ${retries} attempts to ${safeEndpoint(cfg.url)}: ${msg}`);
  }

  const callRaw = async (name: string, args: Record<string, unknown> = {}) => client.callTool({ name, arguments: args });

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
      try {
        await client.close();
      } finally {
        await transport.close();
      }
    },
  };
}
