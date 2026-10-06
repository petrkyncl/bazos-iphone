/** Paralelní map s omezeným počtem souběžných úloh (šetrné k neoficiálním API). */
export async function mapLimit<T, R>(items: T[], limit: number, fn: (item: T) => Promise<R>): Promise<R[]> {
  const out = new Array<R>(items.length);
  let next = 0;
  const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (next < items.length) {
      const i = next++;
      out[i] = await fn(items[i]);
    }
  });
  await Promise.all(workers);
  return out;
}

/** Jednoduchá in-memory cache s expirací (přežívá mezi requesty v rámci jednoho procesu). */
export class TtlCache<V> {
  private map = new Map<string, { value: V; expires: number }>();
  constructor(private ttlMs: number, private maxSize = 5000) {}

  get(key: string): V | undefined {
    const hit = this.map.get(key);
    if (!hit) return undefined;
    if (hit.expires < Date.now()) {
      this.map.delete(key);
      return undefined;
    }
    return hit.value;
  }

  set(key: string, value: V) {
    if (this.map.size >= this.maxSize) {
      const oldest = this.map.keys().next().value;
      if (oldest !== undefined) this.map.delete(oldest);
    }
    this.map.set(key, { value, expires: Date.now() + this.ttlMs });
  }
}

const DIACRITICS: Record<string, string> = {
  á: "a", č: "c", ď: "d", é: "e", ě: "e", í: "i", ň: "n", ó: "o", ř: "r", š: "s", ť: "t", ú: "u", ů: "u", ý: "y", ž: "z",
};

export function stripDiacritics(text: string): string {
  return (text || "").toLowerCase().replace(/[áčďéěíňóřšťúůýž]/g, (c) => DIACRITICS[c] ?? c);
}
