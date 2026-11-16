import type { AiResult } from '../../types/index.js';
import { deriveTitle, extractiveSummary } from '../../utils/text.js';
import type { AiProvider, SummarizeInput } from './provider.js';

/**
 * Deterministic, dependency-free summarizer used when AI is disabled or the
 * provider fails. Never throws, never calls the network.
 */
export class FallbackProvider implements AiProvider {
  readonly name = 'fallback';

  async summarize(input: SummarizeInput): Promise<AiResult> {
    const summary = extractiveSummary(input.description, 600);
    const title =
      input.title.trim().length > 0
        ? input.title.trim().slice(0, 80)
        : deriveTitle(input.description, 80);
    return { title, summary: summary || title, status: 'fallback' };
  }
}
