import type { NextRequest } from "next/server";
import { getDetail, getSellerAds, getSellerReviews } from "@/lib/bazos";
import { evaluateAd } from "@/lib/evaluate";
import { getPricelist } from "@/lib/mp";
import type { AdDetailResponse } from "@/lib/types";

const DEALER_PHONE_ADS = 4; // od kolika telefonů v inzerátech je prodejce nejspíš překupník

export async function GET(_req: NextRequest, ctx: RouteContext<"/api/ads/[id]">) {
  const { id } = await ctx.params;
  if (!/^\d+$/.test(id)) return Response.json({ error: "Neplatné ID" }, { status: 400 });

  try {
    const [detail, pricelist] = await Promise.all([getDetail(id), getPricelist().catch(() => null)]);
    const ad = evaluateAd(detail, pricelist);
    const sellerId = ad.seller.id;
    const [reviews, others] = await Promise.all([
      sellerId && ad.seller.reviewsCount ? getSellerReviews(sellerId).catch(() => []) : [],
      sellerId && ad.seller.totalAds > 1 ? getSellerAds(sellerId).catch(() => []) : [],
    ]);
    const otherAds = others.filter((o) => o.id !== ad.id);
    const phoneAds = 1 + otherAds.filter((o) => o.isPhone).length;
    const body: AdDetailResponse = {
      ad,
      seller: {
        reviews,
        positive: reviews.filter((r) => r.positive).length,
        negative: reviews.filter((r) => !r.positive).length,
        otherAds,
        phoneAds,
        likelyDealer: phoneAds >= DEALER_PHONE_ADS,
      },
    };
    return Response.json(body);
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 502 });
  }
}
