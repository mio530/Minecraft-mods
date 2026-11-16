/** Simple sliding-window rate limiter (requests per minute). */
export class RateLimiter {
  private hits: number[] = [];
  constructor(private perMinute: number) {}

  /** Returns true if a request is allowed now, recording it. */
  tryAcquire(now = Date.now()): boolean {
    if (this.perMinute <= 0) return true;
    const cutoff = now - 60_000;
    this.hits = this.hits.filter((t) => t > cutoff);
    if (this.hits.length >= this.perMinute) return false;
    this.hits.push(now);
    return true;
  }
}
