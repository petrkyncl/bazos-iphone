import { findUnnegated, isReplica } from "./matching";
import { stripDiacritics } from "./util";

/**
 * Odhad stavu telefonu podle textu inzerátu — pravidla, žádné AI (zdarma, okamžitě).
 * Stupně odpovídají výkupu mp.cz:
 *   A+ zánovní (nový, nepoužitý) · A jako nový (bez škrábanců) · B použitý (lehké oděrky)
 *   C opotřebovaný (rýhy, víc oděrek) · D poškozený (prasklý displej/záda, jakákoli funkční vada)
 */
export type Grade = "A+" | "A" | "B" | "C" | "D";
export type Confidence = "vysoka" | "stredni" | "nizka";

export interface Signal {
  grade: Grade | "info";
  label: string;
  match: string;
}

export interface ConditionEstimate {
  grade: Grade;
  confidence: Confidence;
  signals: Signal[];
  batteryPct: number | null;
  warnings: string[];
}

interface Rule {
  grade: Grade;
  label: string;
  re: RegExp;
}

// Vzory pracují s textem bez diakritiky a malými písmeny.
const RULES: Rule[] = [
  // --- D: poškozený / vada ---
  { grade: "D", label: "prasklina", re: /\b(prask\w*|popraskan\w*|roztrisk\w*|rozbit\w*|naprasl\w*)\b/ },
  { grade: "D", label: "nefunkční", re: /\b(nefunkc\w+|nezapin\w+|(nejde|nelze)\s+zapnout|nenabiji|nereaguje|zamrz\w*)\b/ },
  { grade: "D", label: "na díly / opravu", re: /\b(na\s+(nahradni\s+)?dily|na\s+opravu|k\s+oprave|nahradni\s+dily)\b/ },
  { grade: "D", label: "Face ID nefunguje", re: /\b(face\s*id|touch\s*id)\s+(nefung\w*|nejde|nefunkc\w*|nepracuje)|\b(nefung\w*|nejde)\s+(face\s*id|touch\s*id)\b/ },
  { grade: "D", label: "vada displeje", re: /\b(vypalen\w*|burn\s*-?\s*in|mrtv\w+\s+pixel\w*|zelen\w+\s+(car\w*|pruh\w*)|flek\w*\s+na\s+displej\w*|skvrn\w*\s+na\s+displej\w*)\b/ },
  { grade: "D", label: "iCloud blokace", re: /\b(icloud|apple\s*id)\s+(zamcen\w*|blokovan\w*|lock\w*|zablokovan\w*)|\b(zamcen\w*|blokovan\w*)\s+(na\s+)?(icloud|apple\s*id)\b/ },
  { grade: "D", label: "vada", re: /\b(nefunguje|nefunguji|vadn\w+|zavad\w+|poskozen\w+|promacklin\w*|ohnut\w*|tekl\w*\s+voda|polit\w*|utopen\w*)\b/ },

  // --- C: výrazné opotřebení ---
  { grade: "C", label: "opotřebovaný", re: /\b(opotrebovan\w*|(silne|vyrazne|vetsi|hodne)\s+opotreben\w*|ojet\w*|hodne\s+pouzivan\w*)\b/ },
  { grade: "C", label: "výrazné škrábance", re: /\b(hlubsi\w*|hlubok\w+|vyrazn\w+|vetsi\w*|viditeln\w+|vic\w*|vice|hodne|spousta)\s+(skrab\w*|rh\w*|oder\w*|odren\w*|tukan\w*)/ },
  { grade: "C", label: "rýhy / ťukance", re: /\b(ryh\w*|tukan\w*|tuknut\w*|odlupl\w*|odlupnut\w*)\b/ },

  // --- B: lehké známky používání ---
  { grade: "B", label: "známky používání", re: /\b(znamk\w*\s+(pouzivan\w*|pouziti|opotreben\w*)|(bezn\w*|drobn\w*|minimaln\w*|mal\w*|lehk\w*)\s+opotreben\w*)\b/ },
  { grade: "B", label: "škrábance / oděrky", re: /\b(skrab\w*|mikroskrab\w*|oder\w*|odren\w*|otluk\w*|otlac\w*)\b/ },
  { grade: "B", label: "použitý", re: /\b(pouzivan\w*\s+stav|bezny\s+stav|dobry\s+stav|zachovaly\s+stav)\b/ },

  // --- A: jako nový ---
  { grade: "A", label: "jako nový", re: /\b(jako\s+nov\w*|stav\s+nov\w*|perfektni\w*|bezvadn\w*|vyborn\w+\s+stav\w*|top\s+stav\w*|10\s*\/\s*10|100\s*%\s*stav\w*|zanovn\w*|nulov\w+\s+znamk\w*)\b/ },
  { grade: "A", label: "bez škrábanců", re: /\bbez\s+(jakychkoliv\s+|jedineho\s+|zadnych\s+|viditelnych\s+)?(skrab\w*|oder\w*|znamek|poskozeni|vad)\b/ },

  // --- A+: nový, nepoužitý ---
  { grade: "A+", label: "nový nepoužitý", re: /\b(nerozbal\w*|zapecet\w*|neotevren\w*|nepouzit\w*|nerozdel\w*|v\s+foli\w*|sealed|novy\s+kus|zcela\s+novy|uplne\s+novy|brand\s+new)\b/ },
];

// Pozitivní fráze, které obsahují slovo z negativních pravidel a nesmí se počítat
// („bez škrábanců" řeší negace; tady jsou formulace bez negačního slova).
const POSITIVE_MASKS = /\b(odoln\w*\s+proti\s+(skrab\w*|oder\w*)|ochran\w*\s+proti\s+(skrab\w*|oder\w*|prask\w*)|prask\w*\s+(ochrann\w*|tvrzen\w*)\s+skl\w*|(ochrann\w*|tvrzen\w*)\s+skl\w*\s+(je\s+|bylo\s+)?prask\w*|prask\w*\s+sklick\w*|sklick\w*\s+(je\s+|bylo\s+)?prask\w*|prask\w*\s+(kryt\w*|obal\w*|pouzdr\w*)|(kryt\w*|obal\w*|pouzdr\w*)\s+(je\s+)?prask\w*)/g;

const BATTERY = [
  /(?:bater\w*|kondic\w*|zdravi|health|bh|kapacit\w*)\D{0,25}?(\d{2,3})\s*%/,
  /(\d{2,3})\s*%\s*(?:bater\w*|kondic\w*|zdravi|health|bh|kapacit\w*)/,
];

const WARNINGS: [RegExp, string][] = [
  [/\b(neoriginal\w*|neoriginal|kompatibil\w*|aftermarket)\s+(displej\w*|display|baterie|baterii|dil\w*)|\b(vymen\w*|menen\w*|nov\w*)\s+(displej\w*|display)\b/, "Vyměněný / neoriginální díl (mp.cz může snížit stav)"],
  [/\bservisni\s+(zprav\w*|hlasen\w*)|neznam\w*\s+(dil|displej|baterie)/, "Hlášení o neznámém dílu"],
  [/\b(us|usa|americk\w*)\s+(verz\w*|model\w*)|\bjen\s+esim\b|\besim\s+only\b/, "US verze (jen eSIM)"],
  [/\b(blokovan\w*|zamcen\w*)\s+na\s+(operator\w*|sit\w*)|\bsim\s*lock\w*/, "Blokace na operátora"],
  [/\b(icloud|apple\s*id)\b/, "Zmínka o iCloud / Apple ID — ověř odhlášení"],
];

export function estimateCondition(title: string, description: string): ConditionEstimate {
  const raw = `${title}\n${description}`;
  const masked = stripDiacritics(raw).replace(POSITIVE_MASKS, " ");
  const signals: Signal[] = [];
  for (const rule of RULES) {
    // negace („bez škrábanců") ruší jen vady; „bez poškození, jako nový" je pořád „jako nový"
    const hit = rule.grade === "A" || rule.grade === "A+" ? masked.match(rule.re)?.[0] : findUnnegated(rule.re, masked);
    if (hit) signals.push({ grade: rule.grade, label: rule.label, match: hit });
  }

  let batteryPct: number | null = null;
  outer: for (const re of BATTERY) {
    for (const m of masked.matchAll(new RegExp(re.source, "g"))) {
      const v = Number(m[1]);
      // „pod 80 %" je hranice (např. výměna baterie zdarma), ne stav baterie
      const before = masked.slice(0, m.index! + m[0].indexOf(m[1]));
      if (v >= 50 && v <= 100 && !/\b(pod|nad|do|az|alespon|min\w*)\s*$/.test(before)) {
        batteryPct = v;
        break outer;
      }
    }
  }

  const warnings = WARNINGS.filter(([re]) => re.test(masked)).map(([, label]) => label);
  if (isReplica(raw)) warnings.unshift("Replika / napodobenina");

  const has = (g: Grade) => signals.some((s) => s.grade === g);
  let grade: Grade;
  let confidence: Confidence;
  if (has("D")) {
    grade = "D";
    confidence = "vysoka";
  } else if (has("C")) {
    grade = "C";
    confidence = "vysoka";
  } else if (has("B")) {
    // „jako nový, jen mikroškrábanec" = podle mp.cz už B
    grade = "B";
    confidence = has("A") || has("A+") ? "stredni" : "vysoka";
  } else if (has("A+") && !/\bjako\s+nov/.test(masked)) {
    grade = "A+";
    confidence = "stredni";
  } else if (has("A") || has("A+")) {
    grade = "A";
    confidence = "stredni";
  } else {
    grade = "B"; // stav nepopsaný → konzervativně „použitý"
    confidence = "nizka";
  }
  if (batteryPct !== null && batteryPct < 80) warnings.push(`Baterie ${batteryPct} % — pod 80 % může výkup snížit cenu`);

  return { grade, confidence, signals, batteryPct, warnings };
}

// ---------------------------------------------------------------------------
// Šum v inzerátech (port listing_rules.py z bazos-finder)
// ---------------------------------------------------------------------------
export type IssueCode = "prodano" | "rezervovano" | "prodej_rezervace" | "poptavka" | "neni_telefon" | "podezrela_cena";

export const ISSUE_LABELS: Record<IssueCode, string> = {
  prodano: "Prodáno",
  rezervovano: "Rezervováno",
  prodej_rezervace: "Prodej rezervace, ne telefonu",
  poptavka: "Poptávka (Koupím…)",
  neni_telefon: "Není telefon",
  podezrela_cena: "Podezřele nízká cena",
};

const SOLD = /prodano|\bprodan[ay]?\b|\bsold\b/;
const RESERVED = /\bre[sz]ervovan\w*|\bre[sz]erv\b/;
const RESERVATION_TITLE = /\bre[sz]ervac\w*/;
const RESERVATION_SALE = /\b(prenecham|prodam|predam|nabizim|postoupim)\s+(?:\w+\s+){0,2}(?:re[sz]ervac|objednavk|predobjednavk)\w*/;
const BUY_REQUEST = /^\W*(koupim|kupim|shanim|hledam|vykoupim|poptavk\w*|poptavam)\b/;
const OTHER_DEVICE = /\b(ipad|macbook|imac|mac\s*mini|apple\s*watch|watch|airpods?|sluchatk\w*|pencil|tablet\w*|notebook\w*|hodink\w*)\b/;
const ACCESSORY = /\b(kryt\w*|pouzdr\w*|obal\w*|navlek\w*|sklo|skla|sklicko|folie|case|krabic\w*|nabijec\w*|kabel\w*|drzak\w*|powerbank\w*|adapter\w*|protector\w*)\b/;
const SPARE_PART = /\b(displej\w*|display|baterie|nahradni\s+d\w*|leather|sleeve|silikon\w*|magsafe)\b/;
const FILLER = new Set(["prodam", "prodej", "nabizim", "novy", "nova", "nove", "pouzity", "pouzita", "pouzite", "top", "super", "apple"]);

/** Problém inzerátu podle textu (bez ceny), nebo null. */
export function listingIssue(title: string, description: string): IssueCode | null {
  const t = stripDiacritics(title);
  const d = stripDiacritics(description);
  if (SOLD.test(t)) return "prodano";
  if (RESERVED.test(t)) return "rezervovano";
  if (RESERVATION_SALE.test(d) || RESERVATION_SALE.test(t) || RESERVATION_TITLE.test(t)) return "prodej_rezervace";
  if (BUY_REQUEST.test(t)) return "poptavka";
  const first = t.split(/[^a-z0-9]+/).find((w) => w && !FILLER.has(w)) ?? "";
  if (ACCESSORY.test(t) && first !== "iphone" && !/^\d+$/.test(first)) return "neni_telefon";
  if (OTHER_DEVICE.test(t) && !/\bi\s?phone\b/.test(t)) return "neni_telefon";
  return null;
}

/** Cena hluboko pod výkupem: příslušenství/díl, nebo podezřelá cena (chyba, podvod, cena v popisu). */
export function priceIssue(title: string, price: number | null, lowestBuyout: number | null): IssueCode | null {
  if (!price || !lowestBuyout || price >= 0.25 * lowestBuyout) return null;
  const t = stripDiacritics(title);
  return ACCESSORY.test(t) || SPARE_PART.test(t) ? "neni_telefon" : "podezrela_cena";
}
