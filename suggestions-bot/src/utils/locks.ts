/**
 * In-process keyed mutex. Serializes async work per key so that concurrent
 * Discord events (e.g. duplicate thread-create or rapid button clicks on the
 * same suggestion) cannot interleave and corrupt vote counts.
 */
export class KeyedMutex {
  private tails = new Map<string, Promise<void>>();

  run<T>(key: string, fn: () => Promise<T>): Promise<T> {
    const prev = this.tails.get(key) ?? Promise.resolve();
    const result = prev.then(fn, fn);
    // The tail resolves (never rejects) once this task settles.
    const tail = result.then(
      () => undefined,
      () => undefined,
    );
    this.tails.set(key, tail);
    // Free the slot when this is the last queued task.
    void tail.then(() => {
      if (this.tails.get(key) === tail) this.tails.delete(key);
    });
    return result;
  }
}

export const globalLocks = new KeyedMutex();
