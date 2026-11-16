import { describe, expect, it } from 'vitest';
import {
  cosineSimilarity,
  deriveTitle,
  extractiveSummary,
  truncate,
} from '../src/utils/text.js';

describe('text utils', () => {
  it('cosine similarity is high for near-identical text', () => {
    const a = 'please add a teleport command to travel between bases';
    const b = 'add a teleport command to travel between our bases';
    expect(cosineSimilarity(a, b)).toBeGreaterThan(0.7);
  });

  it('cosine similarity is low for unrelated text', () => {
    expect(
      cosineSimilarity('add diamonds to the shop', 'nerf the ender dragon fight'),
    ).toBeLessThan(0.3);
  });

  it('truncate keeps within limit and adds ellipsis', () => {
    const out = truncate('a'.repeat(100), 20);
    expect(out.length).toBeLessThanOrEqual(20);
    expect(out.endsWith('…')).toBe(true);
  });

  it('extractive summary stays within budget', () => {
    const long = Array.from({ length: 20 }, (_, i) => `Sentence number ${i} about mobs and farms.`).join(' ');
    const sum = extractiveSummary(long, 200);
    expect(sum.length).toBeLessThanOrEqual(201);
  });

  it('derives a title from the first sentence', () => {
    expect(deriveTitle('Add bees. And frogs.')).toMatch(/Add bees/);
  });
});
