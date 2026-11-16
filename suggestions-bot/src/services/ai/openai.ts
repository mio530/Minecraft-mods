import type { AiResult } from '../../types/index.js';
import {
  type AiProvider,
  type SummarizeInput,
  SYSTEM_PROMPT,
  buildUserPrompt,
  parseAiJson,
} from './provider.js';

export class OpenAiProvider implements AiProvider {
  readonly name = 'openai';
  constructor(
    private apiKey: string,
    private model: string,
  ) {}

  async summarize(input: SummarizeInput): Promise<AiResult> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 20_000);
    try {
      const res = await fetch('https://api.openai.com/v1/chat/completions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${this.apiKey}`,
        },
        body: JSON.stringify({
          model: this.model,
          temperature: 0.2,
          response_format: { type: 'json_object' },
          messages: [
            { role: 'system', content: SYSTEM_PROMPT },
            { role: 'user', content: buildUserPrompt(input) },
          ],
        }),
        signal: controller.signal,
      });
      if (!res.ok) {
        throw new Error(`OpenAI HTTP ${res.status}`);
      }
      const data = (await res.json()) as {
        choices?: { message?: { content?: string } }[];
      };
      const content = data.choices?.[0]?.message?.content ?? '';
      const parsed = parseAiJson(content);
      return { ...parsed, status: 'done' };
    } finally {
      clearTimeout(timeout);
    }
  }
}
