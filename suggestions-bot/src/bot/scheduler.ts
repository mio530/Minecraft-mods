import { logger } from '../utils/logger.js';
import { evaluate } from '../services/evaluation.js';
import { buildLogEvaluation } from './components/embeds.js';
import type { BotContext } from './context.js';

/**
 * Periodically closes expired votings and posts a (non-binding) evaluation
 * summary to the log channel. Staff still makes the final decision.
 */
export class EvaluationScheduler {
  private timer: NodeJS.Timeout | null = null;
  private running = false;

  constructor(private ctx: BotContext) {}

  start(): void {
    const minutes = Math.max(1, this.ctx.config.voting.evaluationIntervalMinutes);
    this.timer = setInterval(() => void this.tick(), minutes * 60_000);
    // Run once shortly after startup to catch votings that expired while offline.
    setTimeout(() => void this.tick(), 10_000);
    logger.info(`Evaluation scheduler started (every ${minutes} min)`);
  }

  stop(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }

  async tick(): Promise<void> {
    if (this.running) return;
    this.running = true;
    try {
      const expired = this.ctx.store.listExpired(Date.now());
      for (const s of expired) {
        this.ctx.store.setVotingClosed(s.id, true);
        const updated = this.ctx.store.getSuggestion(s.id)!;
        await this.ctx.refreshVoting(updated);
        const totals = this.ctx.store.getTotals(s.id);
        await this.ctx.log(buildLogEvaluation(updated, totals, evaluate(this.ctx.config, totals)));
        logger.info(`Auto-closed voting for ${s.id}`);
      }
    } catch (err) {
      logger.error('Evaluation tick failed:', String(err));
    } finally {
      this.running = false;
    }
  }
}
