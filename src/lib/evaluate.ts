import "server-only";
import type { BazosDetail } from "./bazos";
import { parsePrice, toIsoDate } from "./bazos";
import { estimateCondition, listingIssue, priceIssue, ISSUE_LABELS, type IssueCode } from "./condition";
import { modelLabel, parseIphone } from "./matching";
import { matchPrice, type Pricelist } from "./mp";
import type { EvaluatedAd } from "./types";

const EXCLUDING: IssueCode[] = ["prodano", "rezervovano", "prodej_rezervace", "poptavka", "neni_telefon"];

/** Detail inzerátu z Bazoše → inzerát s modelem, odhadem stavu, výkupní cenou a ziskem. */
export function evaluateAd(d: BazosDetail, pricelist: Pricelist | null): EvaluatedAd {
  const title = (d.title ?? "").replace(/\s+/g, " ").trim();
  const description = (d.description ?? "").replace(/\r\n/g, "\n").trim();
  const price = d.price_type === "EXACT" && d.price ? Number(d.price) : parsePrice(d.price_formatted);

  // model z nadpisu; když v něm není, zkusit i popis
  let key = parseIphone(title);
  if (!key?.number) key = parseIphone(`${title} ${description}`) ?? key;
  const condition = estimateCondition(title, description);
  const match = key?.number && pricelist ? matchPrice(key, pricelist) : null;

  let issue = listingIssue(title, description);
  // podezřelá cena = hluboko pod nejnižším výkupem funkčního kusu (A+–C)
  const working = match ? (["A+", "A", "B", "C"] as const).map((g) => match.item.buy[g]).filter((v): v is number => !!v) : [];
  issue ??= priceIssue(title, price, working.length ? Math.min(...working) : null);

  const isReplica = condition.warnings.includes("Replika / napodobenina");
  const buyout = match ? match.item.buy[condition.grade] ?? null : null;
  const profit = buyout != null && price ? buyout - price : null;

  let deal: EvaluatedAd["deal"] = "unknown";
  if (issue && EXCLUDING.includes(issue)) deal = "excluded";
  else if (isReplica) deal = "excluded";
  else if (profit !== null) deal = issue === "podezrela_cena" ? "suspicious" : profit > 0 ? "good" : "bad";

  return {
    id: String(d.id),
    url: d.url,
    title,
    description,
    price,
    priceText: d.price_formatted ?? null,
    // u topovaných inzerátů je „from" čas posledního topování, ne vložení → nezobrazovat
    date: d.topped === true || d.topped === "true" ? null : toIsoDate(d.from),
    isTop: d.topped === true || d.topped === "true",
    views: Number(d.views) || 0,
    images: d.images ?? [],
    thumb: d.images?.[0]?.replace("/img/1/", "/img/1m/") ?? null, // 350px náhled místo plné fotky
    locality: d.locality ?? null,
    zip: d.zip_code ?? null,
    lat: d.latitude ? Number(d.latitude) : null,
    lng: d.longitude ? Number(d.longitude) : null,
    seller: {
      id: d.phone_id ?? null,
      name: d.name ?? null,
      totalAds: Number(d.user_ads_count) || 0,
      reviewsCount: Number(d.user_reviews_count) || 0,
    },
    model: key?.number ? modelLabel(key) : null,
    storageGb: key?.storageGb ?? null,
    condition,
    buyoutModel: match?.item.variant ?? null,
    buyoutImage: match?.item.image ?? null,
    buyoutUrl: match?.item.url ?? null,
    storageGuessed: match?.storageGuessed ?? false,
    buyPrices: match?.item.buy ?? null,
    sellPrices: match?.item.sell ?? null,
    buyout,
    profit,
    issue: issue ? { code: issue, label: ISSUE_LABELS[issue] } : null,
    deal,
  };
}
