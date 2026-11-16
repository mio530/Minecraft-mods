import type { AiResult } from '../../types/index.js';
import {
  type AiProvider,
  type SummarizeInput,
  SYSTEM_PROMPT,
  buildUserPrompt,
  parseAiJson,
} from './provider.js';

export class AnthropicProvider implements AiProvider {
  readonly name = 'anthropic';
  constructor(
    private apiKey: string,
    private model: string,
  ) {}

  async summarize(input: SummarizeInput): Promise<AiResult> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 20_000);
    try {
      const res = await fetch('https://api.anthropic.com/v1/messages', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-api-key': this.apiKey,
          'anthropic-version': '2023-06-01',
        },
        body: JSON.stringify({
          model: this.model,
          max_tokens: 1024,
          temperature: 0.2,
          system: SYSTEM_PROMPT,
          messages: [{ role: 'user', content: buildUserPrompt(input) }],
        }),
        signal: controller.signal,
      });
      if (!res.ok) {
        throw new Error(`Anthropic HTTP ${res.status}`);
      }
      const data = (await res.json()) as { content?: { text?: string }[] };
      const content = data.content?.map((c) => c.text ?? '').join('') ?? '';
      const parsed = parseAiJson(content);
      return { ...parsed, status: 'done' };
    } finally {
      clearTimeout(timeout);
    }
  }
}
