import { stripDiacritics } from "./util";

/**
 * Párování iPhonů na výkupní ceník mp.cz. Port odladěné logiky z projektu bazos-finder
 * (matching.py): strukturní klíč rodina + číslo + varianta + úložiště místo porovnávání slov.
 */

export interface IphoneKey {
  number: number | null; // generace (X/XR/XS = 10, Air = 17, SE = rok vydání)
  variants: string[]; // seřazené: pro, max, mini, plus, e, x, xr, xs, air
  storageGb: number | null;
}

const STORAGE_VALUES = new Set([16, 32, 64, 128, 256, 512, 1024]);
const VARIANT_WORDS = new Set(["pro", "max", "mini", "plus"]);

export function normalize(text: string): string {
  let t = stripDiacritics(text);
  t = t.replace(/\+/g, " ");
  t = t.replace(/([a-z])(\d)/g, "$1 $2").replace(/(\d)([a-z])/g, "$1 $2");
  t = t.replace(/[^a-z0-9/]+/g, " ");
  return t.replace(/\s+/g, " ").trim();
}

function extractStorage(norm: string): number | null {
  if (/\b1\s*tb\b/.test(norm)) return 1024;
  if (/\b2\s*tb\b/.test(norm)) return 2048;
  for (const m of norm.matchAll(/\b(\d{2,4})\s*(gb)?\b/g)) {
    const v = Number(m[1]);
    if (STORAGE_VALUES.has(v) && v >= 32) return v;
  }
  return null;
}

/** Rozparsuje název iPhonu. `null`, když text iPhone neobsahuje. */
export function parseIphone(text: string): IphoneKey | null {
  // „I PHONE", „i-phone" → iphone
  const norm = normalize(text).replace(/\bi phone\b/g, "iphone");
  const tokens = norm.split(/[\s/]+/);
  const idx = tokens.indexOf("iphone");
  if (idx < 0) return null;
  const after = tokens.slice(idx + 1);
  const variants = new Set<string>();
  let number: number | null = null;
  let numberPos = -1;
  const first = after[0] ?? "";

  if (after.slice(0, 3).includes("air")) {
    number = 17; // „iPhone Air", „iPhone 17 Air"
    variants.add("air");
  } else if (first === "x" || first === "xr" || first === "xs") {
    number = 10;
    variants.add(first);
  } else if (first === "se") {
    let year: number | null = after.map(Number).find((n) => n === 2016 || n === 2020 || n === 2022) ?? null;
    if (year === null) {
      const gen = after.slice(1, 3).find((t) => t === "1" || t === "2" || t === "3");
      year = gen ? ({ "1": 2016, "2": 2020, "3": 2022 } as Record<string, number>)[gen] : null;
    }
    if (year === null && after.includes("5") && after.includes("g")) year = 2022;
    number = year; // neznámý rok → nespárovat (nehádat mezi 2020 a 2022)
  } else {
    numberPos = after.findIndex((t) => /^\d+$/.test(t) && Number(t) >= 1 && Number(t) <= 30);
    if (numberPos >= 0) number = Number(after[numberPos]);
  }

  // varianty hledáme jen kousek za číslem modelu (ne „max" z „max. stav" v půlce nadpisu)
  const window = after.slice(0, (numberPos >= 0 ? numberPos : 0) + 4);
  for (const t of window) if (VARIANT_WORDS.has(t)) variants.add(t);
  if (numberPos >= 0 && after[numberPos + 1] === "e") variants.add("e"); // 16e, ne „e sim"
  if (!variants.has("plus") && /iphone\s*\d{1,2}\s*\+/i.test(text)) variants.add("plus");

  return { number, variants: [...variants].sort(), storageGb: extractStorage(norm) };
}

export function sameModel(a: IphoneKey, b: IphoneKey): boolean {
  return a.number !== null && a.number === b.number && a.variants.join() === b.variants.join();
}

export function modelLabel(k: IphoneKey): string {
  const v = k.variants;
  if (v.includes("x") || v.includes("xr") || v.includes("xs")) {
    return `iPhone ${v.filter((x) => x !== "max").join("").toUpperCase()}${v.includes("max") ? " Max" : ""}`;
  }
  if (v.includes("air")) return "iPhone Air";
  if (k.number && k.number > 2000) return `iPhone SE (${k.number})`;
  const order = ["mini", "plus", "pro", "max"];
  const words = order.filter((w) => v.includes(w)).map((w) => w[0].toUpperCase() + w.slice(1));
  return `iPhone ${k.number}${v.includes("e") ? "e" : ""}${words.length ? " " + words.join(" ") : ""}`;
}

// ---------------------------------------------------------------------------
// Hledání frází s ohledem na negaci („bez škrábanců", „nejedná se o repliku")
// ---------------------------------------------------------------------------
const NEG_CUE = /\b(bez|beze|zadn\w*|neni|nejsou|nema|nejde|nelze|nejedna\s+se|na\s+rozdil|ani)\b/g;
const CONTRAST = /\b(ale|avsak|vsak|ovsem|bohuzel|jenze|nicmene|krome)\b/;
const SENTENCE_BOUNDARY = /[.!?\n\r|;]/g;

function clauseNegated(text: string, start: number): boolean {
  let boundary = -1;
  for (const m of text.slice(0, start).matchAll(SENTENCE_BOUNDARY)) boundary = m.index!;
  const span = text.slice(boundary + 1, start);
  let lastCueEnd = -1;
  for (const m of span.matchAll(NEG_CUE)) lastCueEnd = m.index! + m[0].length;
  if (lastCueEnd < 0) return false;
  return !CONTRAST.test(span.slice(lastCueEnd)); // „bez vady, ALE displej nefunkční"
}

/** Najde první nepopřený výskyt vzoru (text bez diakritiky). Vrací nalezený úryvek nebo null. */
export function findUnnegated(pattern: RegExp, text: string): string | null {
  const t = stripDiacritics(text);
  const re = new RegExp(pattern.source, pattern.flags.includes("g") ? pattern.flags : pattern.flags + "g");
  for (const m of t.matchAll(re)) if (!clauseNegated(t, m.index!)) return m[0];
  return null;
}

const REPLICA = /\b(replik\w*|kopi[eiju]\w*|napodoben\w*|fake|falesn\w+|klon\w*|1\s*:\s*1|cinsk\w+\s+verz\w*|hong\s*kong)\b/;

export function isReplica(text: string): boolean {
  return findUnnegated(REPLICA, text) !== null;
}
