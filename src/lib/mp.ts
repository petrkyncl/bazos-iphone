import "server-only";
import { promises as fs } from "fs";
import path from "path";
import { mapLimit } from "./util";
import { parseIphone, sameModel, type IphoneKey } from "./matching";
import type { Grade } from "./condition";

/**
 * Výkupní ceník iPhonů z mp.cz (neoficiální API průvodce výkupem, viz bazos-finder/docs/mp-cz-vykupni-ceny-api.md).
 * Strom: meta-masters (modely) → attr-choices (kapacity) → color-choices (barvy) → qualities (ceny po stavech).
 * Celý Apple ceník ≈ 250 požadavků → stahuje se jednou denně a drží na disku i v paměti.
 */
const MP = "https://www.mp.cz";
const API = `${MP}/api/new/buyout/product-selector`;
const CACHE_FILE = path.join(process.cwd(), "data", "mp-apple.json");
const MAX_AGE_MS = 24 * 3600_000;
const NO_BUYOUT = 10; // výkup 10 Kč = fakticky nevykupují

export const GRADES: Grade[] = ["A+", "A", "B", "C", "D"];

export interface PriceVariant {
  model: string; // "Apple iPhone 15 Pro"
  variant: string; // "Apple iPhone 15 Pro, 128GB"
  storageGb: number | null;
  buy: Partial<Record<Grade, number>>; // kolik mp.cz zaplatí
  sell: Partial<Record<Grade, number>>; // za kolik mp.cz prodává (jen A+–C, věrohodné)
  image: string | null;
  url: string | null;
}

export interface Pricelist {
  updatedAt: string;
  items: PriceVariant[];
}

async function mpGet<T>(p: string): Promise<T> {
  const res = await fetch(`${API}/${p}`, {
    headers: { "User-Agent": "Mozilla/5.0 (compatible; price-check)" },
    cache: "no-store",
    signal: AbortSignal.timeout(20_000),
  });
  if (!res.ok) throw new Error(`mp.cz ${p}: HTTP ${res.status}`);
  return (await res.json()) as T;
}

const abs = (p?: string | null) => (p ? (p.startsWith("http") ? p : `${MP}${p.startsWith("/") ? "" : "/"}${p}`) : null);

interface MetaMaster { name: string; seo_name: string; image?: string }
interface AttrChoices { attr_values?: { attribute_choice?: { value?: string }; master_product?: { name?: string; seo_name?: string } }[] }
interface Color { seo_name: string; image?: string }
interface Quality { price?: string | null; buying_price?: string | null; seo_name?: string; quality?: { short_name?: string } }

async function downloadPricelist(): Promise<Pricelist> {
  const { meta_masters: models } = await mpGet<{ meta_masters: MetaMaster[] }>("meta-masters/telefony/apple/");
  const variants = (
    await mapLimit(models, 8, async (m) => {
      try {
        const data = await mpGet<AttrChoices>(`attr-choices/${m.seo_name}/`);
        return (data.attr_values ?? [])
          .filter((v) => v.master_product?.seo_name)
          .map((v) => ({ model: m, name: v.master_product!.name ?? m.name, seo: v.master_product!.seo_name! }));
      } catch {
        return [];
      }
    })
  ).flat();

  const items = await mapLimit(variants, 8, async (v): Promise<PriceVariant | null> => {
    try {
      const colors = await mpGet<Color[]>(`color-choices/${v.seo}/`);
      if (!colors.length) return null;
      const qualities = await mpGet<Quality[]>(`qualities/${colors[0].seo_name}/`); // ceny jsou pro všechny barvy stejné
      const buy: PriceVariant["buy"] = {};
      const sell: PriceVariant["sell"] = {};
      for (const q of qualities) {
        const g = q.quality?.short_name as Grade | undefined;
        if (!g || !GRADES.includes(g)) continue;
        const b = q.buying_price != null ? Math.round(Number(q.buying_price)) : 0;
        const s = q.price != null ? Math.round(Number(q.price)) : 0;
        if (b > NO_BUYOUT) buy[g] = b;
        if (s > 0 && g !== "D") sell[g] = s; // prodej D je nespolehlivý
      }
      if (!Object.keys(buy).length) return null;
      return {
        model: v.model.name,
        variant: v.name,
        storageGb: parseIphone(v.name)?.storageGb ?? null,
        buy,
        sell,
        image: abs(colors[0].image ?? v.model.image),
        url: abs(colors[0].seo_name),
      };
    } catch {
      return null;
    }
  });

  return { updatedAt: new Date().toISOString(), items: items.filter((x): x is PriceVariant => x !== null) };
}

let memory: Pricelist | null = null;
let refreshing: Promise<Pricelist> | null = null;

async function readDisk(): Promise<Pricelist | null> {
  try {
    return JSON.parse(await fs.readFile(CACHE_FILE, "utf-8")) as Pricelist;
  } catch {
    return null;
  }
}

async function refresh(): Promise<Pricelist> {
  refreshing ??= (async () => {
    try {
      const fresh = await downloadPricelist();
      // pojistka proti částečnému výpadku API: výrazně menší ceník nepřepíše starý
      if (memory && fresh.items.length < memory.items.length * 0.5) return memory;
      await fs.mkdir(path.dirname(CACHE_FILE), { recursive: true });
      await fs.writeFile(CACHE_FILE, JSON.stringify(fresh, null, 1));
      memory = fresh;
      return fresh;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

/** Ceník: z paměti/disku; zastaralý se vrátí hned a obnoví na pozadí, chybějící se stáhne. */
export async function getPricelist(force = false): Promise<Pricelist> {
  memory ??= await readDisk();
  if (!memory || force) return refresh();
  if (Date.now() - new Date(memory.updatedAt).getTime() > MAX_AGE_MS) refresh().catch(() => {});
  return memory;
}

export interface PriceMatch {
  item: PriceVariant;
  storageGuessed: boolean; // úložiště v inzerátu chybí/nesedí → vybrána nejbližší/nejlevnější varianta
}

const priceA = (i: PriceVariant) => i.buy.A ?? i.buy["A+"] ?? i.buy.B ?? Infinity;

const keyCache = new Map<string, IphoneKey | null>();
const variantKey = (name: string) => {
  if (!keyCache.has(name)) keyCache.set(name, parseIphone(name));
  return keyCache.get(name)!;
};

export function matchPrice(key: IphoneKey, pricelist: Pricelist): PriceMatch | null {
  const candidates = pricelist.items.filter((i) => {
    const k = variantKey(i.variant);
    return k !== null && sameModel(k, key);
  });
  if (!candidates.length) return null;
  if (key.storageGb !== null) {
    const exact = candidates.filter((c) => c.storageGb === key.storageGb);
    if (exact.length) return { item: exact.sort((a, b) => priceA(a) - priceA(b))[0], storageGuessed: false };
    const nearest = [...candidates].sort(
      (a, b) => Math.abs((a.storageGb ?? 0) - key.storageGb!) - Math.abs((b.storageGb ?? 0) - key.storageGb!) || priceA(a) - priceA(b),
    )[0];
    return { item: nearest, storageGuessed: true };
  }
  // úložiště neznáme → konzervativně nejlevnější varianta
  return { item: [...candidates].sort((a, b) => priceA(a) - priceA(b))[0], storageGuessed: true };
}
