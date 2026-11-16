import type { AiResult } from '../../types/index.js';

export interface SummarizeInput {
  title: string;
  description: string;
}

export interface AiProvider {
  readonly name: string;
  /** Returns an AiResult, or throws on hard failure (caller handles fallback). */
  summarize(input: SummarizeInput): Promise<AiResult>;
}

export const SYSTEM_PROMPT = [
  'You rewrite Minecraft server community suggestions into a clean, neutral summary.',
  'Rules:',
  '- Keep the original meaning completely; do NOT invent new ideas or features.',
  '- Remove filler, fix spelling and grammar, keep it concise and understandable.',
  '- Write neutrally (no opinion, no approval/rejection).',
  '- Answer in the same language as the suggestion.',
  'Return ONLY compact JSON: {"title": string (<=80 chars), "summary": string (<=600 chars)}.',
].join('\n');

export function buildUserPrompt(input: SummarizeInput): string {
  return [
    `Original title: ${input.title}`,
    '',
    'Original suggestion:',
    input.description,
  ].join('\n');
}

/** Parse a model response that should contain a JSON object. */
export function parseAiJson(raw: string): { title: string; summary: string } {
  const match = raw.match(/\{[\s\S]*\}/);
  if (!match) throw new Error('AI response did not contain JSON');
  const obj = JSON.parse(match[0]) as { title?: unknown; summary?: unknown };
  const title = typeof obj.title === 'string' ? obj.title.trim() : '';
  const summary = typeof obj.summary === 'string' ? obj.summary.trim() : '';
  if (!title || !summary) throw new Error('AI JSON missing title or summary');
  return { title: title.slice(0, 80), summary: summary.slice(0, 600) };
}
