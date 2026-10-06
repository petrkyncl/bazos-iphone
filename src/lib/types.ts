// Typy sdílené mezi serverem (route handlers) a klientem.
import type { ConditionEstimate, Grade } from "./condition";

export type { ConditionEstimate, Grade };

export type Deal = "good" | "bad" | "suspicious" | "unknown" | "excluded";

export interface EvaluatedAd {
  id: string;
  url: string;
  title: string;
  description: string;
  price: number | null;
  priceText: string | null;
  date: string | null;
  isTop: boolean;
  views: number;
  images: string[];
  locality: string | null;
  zip: string | null;
  lat: number | null;
  lng: number | null;
  seller: {
    id: string | null;
    name: string | null;
    totalAds: number;
    reviewsCount: number;
  };
  model: string | null; // rozpoznaný model, např. „iPhone 15 Pro"
  storageGb: number | null;
  condition: ConditionEstimate;
  buyoutModel: string | null; // varianta v ceníku mp.cz
  buyoutImage: string | null;
  buyoutUrl: string | null;
  storageGuessed: boolean;
  buyPrices: Partial<Record<Grade, number>> | null;
  sellPrices: Partial<Record<Grade, number>> | null;
  buyout: number | null; // výkup v odhadnutém stavu
  profit: number | null; // výkup − cena
  issue: { code: string; label: string } | null;
  deal: Deal;
}

export interface SellerProfile {
  reviews: { text: string; positive: boolean; author?: string; date?: string | null }[];
  positive: number;
  negative: number;
  otherAds: { id: string; title: string; price: number | null; url: string; image?: string; isPhone: boolean }[];
  phoneAds: number; // kolik telefonů prodejce nabízí (včetně tohoto)
  likelyDealer: boolean;
}

export interface AdsResponse {
  total: number;
  offset: number;
  ads: EvaluatedAd[];
  pricelistUpdatedAt: string | null;
}

export interface AdDetailResponse {
  ad: EvaluatedAd;
  seller: SellerProfile;
}
