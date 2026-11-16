import { describe, expect, it } from 'vitest';
import { evaluate } from '../src/services/evaluation.js';
import { testConfig } from './helpers.js';

describe('evaluate', () => {
  it('flags a positive community signal when all thresholds met', () => {
    const cfg = testConfig(); // min votes 3, min upvotes 2, ratio 60
    const hints = evaluate(cfg, { upvotes: 8, downvotes: 2, total: 10, positiveRatio: 80 });
    expect(hints.some((h) => h.includes('POSITIVE'))).toBe(true);
  });

  it('is inconclusive below thresholds', () => {
    const cfg = testConfig();
    const hints = evaluate(cfg, { upvotes: 1, downvotes: 0, total: 1, positiveRatio: 100 });
    expect(hints.some((h) => h.includes('inconclusive'))).toBe(true);
  });

  it('never auto-approves (only guidance)', () => {
    const cfg = testConfig();
    const hints = evaluate(cfg, { upvotes: 100, downvotes: 0, total: 100, positiveRatio: 100 });
    expect(hints.join(' ')).toMatch(/staff decides/i);
  });
});
