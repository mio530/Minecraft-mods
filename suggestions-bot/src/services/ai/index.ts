import type { BotConfig } from '../../config/index.js';
import type { AiResult, Suggestion } from '../../types/index.js';
import { cosineSimilarity } from '../../utils/text.js';
import { logger } from '../../utils/logger.js';
import { AnthropicProvider } from './anthropic.js';
import { FallbackProvider } from './fallback.js';
import { OpenAiProvider } from './openai.js';
import type { AiProvider, SummarizeInput } from './provider.js';
import { RateLimiter } from './rateLimiter.js';

export interface DuplicateMatch {
  suggestion: Suggestion;
  similarity: number;
}

function buildProvider(cfg: BotConfig): AiProvider {
  switch (cfg.ai.provider) {
    case 'openai':
      return new OpenAiProvider(cfg.ai.apiKey, cfg.ai.model);
    case 'anthropic':
      return new AnthropicProvider(cfg.ai.apiKey, cfg.ai.model);
    default:
      return new FallbackProvider();
  }
}

/**
 * Orchestrates AI summarization with rate limiting and a guaranteed fallback,
 * plus deterministic local duplicate detection (no API cost).
 */
export class AiService {
  private provider: AiProvider;
  private fallback = new FallbackProvider();
  private limiter: RateLimiter;

  constructor(
    private cfg: BotConfig,
    provider?: AiProvider,
  ) {
    this.provider = provider ?? buildProvider(cfg);
    this.limiter = new RateLimiter(cfg.ai.rateLimitPerMinute);
  }

  get providerName(): string {
    return this.provider.name;
  }

  /**
   * Summarize a suggestion. Always resolves (never throws): on rate-limit or
   * provider error it returns the deterministic fallback result.
   */
  async summarize(input: SummarizeInput): Promise<AiResult> {
    if (this.provider.name === 'fallback') {
      return this.fallback.summarize(input);
    }
    if (!this.limiter.tryAcquire()) {
      logger.warn('AI rate limit reached; using fallback summarizer');
      return this.fallback.summarize(input);
    }
    try {
      return await this.provider.summarize(input);
    } catch (err) {
      logger.warn(`AI provider "${this.provider.name}" failed, using fallback:`, String(err));
      const fb = await this.fallback.summarize(input);
      return { ...fb, status: 'failed' };
    }
  }

  /**
   * Detect similar/duplicate suggestions among recent ones using local cosine
   * similarity. Returns matches at or above the configured threshold.
   */
  findDuplicates(
    candidate: SummarizeInput,
    recent: Suggestion[],
    excludeId?: string,
  ): DuplicateMatch[] {
    const text = `${candidate.title} ${candidate.description}`;
    const matches: DuplicateMatch[] = [];
    for (const s of recent) {
      if (s.id === excludeId) continue;
      const other = `${s.originalTitle} ${s.aiSummary ?? s.originalDescription}`;
      const similarity = cosineSimilarity(text, other);
      if (similarity >= this.cfg.ai.similarityThreshold) {
        matches.push({ suggestion: s, similarity });
      }
    }
    return matches.sort((a, b) => b.similarity - a.similarity);
  }
}
