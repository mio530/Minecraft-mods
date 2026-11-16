/**
 * Lightweight, dependency-free text utilities used for the deterministic
 * fallback summarizer and for duplicate detection.
 */

const STOPWORDS = new Set([
  'the', 'a', 'an', 'and', 'or', 'but', 'is', 'are', 'was', 'were', 'be', 'to',
  'of', 'in', 'on', 'for', 'with', 'that', 'this', 'it', 'as', 'at', 'by',
  'der', 'die', 'das', 'und', 'oder', 'ein', 'eine', 'ist', 'im', 'auf', 'fuer',
  'für', 'mit', 'den', 'dem', 'zu', 'man', 'wir', 'ich', 'es', 'nicht',
]);

export function tokenize(text: string): string[] {
  return text
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[^\p{L}\p{N}\s]/gu, ' ')
    .split(/\s+/)
    .filter((t) => t.length > 1 && !STOPWORDS.has(t));
}

/** Cosine similarity over bag-of-words term frequency vectors. 0..1 */
export function cosineSimilarity(a: string, b: string): number {
  const ta = tokenize(a);
  const tb = tokenize(b);
  if (ta.length === 0 || tb.length === 0) return 0;

  const fa = new Map<string, number>();
  const fb = new Map<string, number>();
  for (const t of ta) fa.set(t, (fa.get(t) ?? 0) + 1);
  for (const t of tb) fb.set(t, (fb.get(t) ?? 0) + 1);

  let dot = 0;
  for (const [t, v] of fa) {
    const w = fb.get(t);
    if (w) dot += v * w;
  }
  const mag = (m: Map<string, number>) =>
    Math.sqrt([...m.values()].reduce((s, v) => s + v * v, 0));
  const denom = mag(fa) * mag(fb);
  return denom === 0 ? 0 : dot / denom;
}

/** Collapse whitespace and trim. */
export function normalizeWhitespace(s: string): string {
  return s.replace(/\s+/g, ' ').trim();
}

/** Basic, conservative spelling/spacing cleanup for the fallback summarizer. */
export function basicCleanup(s: string): string {
  return normalizeWhitespace(s)
    .replace(/\s+([.,!?;:])/g, '$1')
    .replace(/([.,!?;:])(?=[^\s])/g, '$1 ')
    .replace(/\s+/g, ' ')
    .trim();
}

/** Truncate to a max length without cutting words, adding an ellipsis. */
export function truncate(s: string, max: number): string {
  if (s.length <= max) return s;
  const cut = s.slice(0, max - 1);
  const lastSpace = cut.lastIndexOf(' ');
  return (lastSpace > max * 0.6 ? cut.slice(0, lastSpace) : cut).trim() + '…';
}

/**
 * Deterministic extractive summary: pick the most informative sentences
 * (by non-stopword term frequency) until the length budget is reached.
 */
export function extractiveSummary(text: string, maxChars = 600): string {
  const clean = basicCleanup(text);
  const sentences = clean
    .split(/(?<=[.!?])\s+/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
  if (sentences.length <= 2) return truncate(clean, maxChars);

  const df = new Map<string, number>();
  for (const s of sentences) {
    for (const t of new Set(tokenize(s))) df.set(t, (df.get(t) ?? 0) + 1);
  }
  const scored = sentences.map((s, idx) => {
    const toks = tokenize(s);
    const score = toks.reduce((sum, t) => sum + (df.get(t) ?? 0), 0) / (toks.length || 1);
    return { s, idx, score };
  });
  const top = [...scored].sort((a, b) => b.score - a.score);

  const chosen: typeof scored = [];
  let len = 0;
  for (const item of top) {
    if (len + item.s.length > maxChars && chosen.length > 0) break;
    chosen.push(item);
    len += item.s.length + 1;
    if (chosen.length >= 4) break;
  }
  chosen.sort((a, b) => a.idx - b.idx);
  return truncate(chosen.map((c) => c.s).join(' '), maxChars);
}

/** Derive a short title from text. */
export function deriveTitle(text: string, max = 80): string {
  const clean = basicCleanup(text);
  const firstSentence = clean.split(/(?<=[.!?])\s+/)[0] ?? clean;
  return truncate(firstSentence, max);
}
