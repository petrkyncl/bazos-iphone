import type { NextRequest } from "next/server";
import { getDetail, searchAds, type SearchFilters } from "@/lib/bazos";
import { evaluateAd } from "@/lib/evaluate";
import { getPricelist } from "@/lib/mp";
import type { AdsResponse, EvaluatedAd } from "@/lib/types";
import { mapLimit } from "@/lib/util";

const PAGE = 30;
const DETAIL_CONCURRENCY = 6;

export async function GET(req: NextRequest) {
  const sp = req.nextUrl.searchParams;
  const offset = Math.max(0, Number(sp.get("offset")) || 0);
  const sort = sp.get("sort");
  const filters: SearchFilters = {
    query: sp.get("query") || "iphone",
    price_from: sp.get("price_from") ?? undefined,
    price_to: sp.get("price_to") ?? undefined,
    zip_code: sp.get("zip") ?? undefined,
    distance: sp.get("distance") ?? undefined,
    sort: sort === "price_asc" || sort === "price_desc" ? sort : "date",
  };

  try {
    const [{ items, total }, pricelist] = await Promise.all([
      searchAds(filters, offset, PAGE),
      getPricelist().catch(() => null),
    ]);
    // popis, přesná cena a fotky jsou jen v detailu → stáhnout detaily (s cache)
    const ads = await mapLimit(items, DETAIL_CONCURRENCY, async (item) => {
      try {
        return evaluateAd(await getDetail(item.id), pricelist);
      } catch {
        return null;
      }
    });
    const body: AdsResponse = {
      total,
      offset,
      ads: ads.filter((a): a is EvaluatedAd => a !== null),
      pricelistUpdatedAt: pricelist?.updatedAt ?? null,
    };
    return Response.json(body);
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 502 });
  }
}
