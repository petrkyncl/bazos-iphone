import "server-only";
import { TtlCache } from "./util";

/**
 * Klient mobilního API Bazoše (zjištěno dekompilací Android appky 2.26.1, viz docs/bazos-api.md).
 * Čtecí endpointy nepotřebují přihlášení. API je neoficiální → cache a málo souběžných požadavků.
 */
const BASE = "https://www.bazos.cz/api/v2/";
export const SECTION_MOBILES = "MO";
export const CATEGORY_APPLE = "439";

type Params = Record<string, string | number | undefined | null>;

async function get<T>(endpoint: string, params: Params = {}): Promise<{ data: T; headers: Headers }> {
  const qs = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== null && v !== "") qs.set(k, String(v));
  const res = await fetch(`${BASE}${endpoint}?${qs}`, {
    headers: { "User-Agent": "ktor-client", Accept: "application/json" },
    cache: "no-store",
    signal: AbortSignal.timeout(20_000),
  });
  if (!res.ok) throw new Error(`Bazoš ${endpoint}: HTTP ${res.status}`);
  return { data: (await res.json()) as T, headers: res.headers };
}

export interface BazosListItem {
  id: string;
  title: string;
  price_formatted: string;
  image_thumbnail?: string;
  locality?: string;
  topped?: string;
  url: string;
  views?: string;
}

export interface BazosDetail {
  id: string;
  from?: string;
  status?: string;
  topped?: boolean | string;
  title: string;
  description?: string;
  price_formatted?: string;
  price?: string;
  price_type?: string;
  name?: string;
  phone_id?: string;
  zip_code?: string;
  locality?: string;
  latitude?: string;
  longitude?: string;
  url: string;
  views?: string;
  images?: string[];
  user_ads_count?: string;
  user_reviews_count?: string;
}

export interface SearchFilters {
  query?: string;
  price_from?: string;
  price_to?: string;
  zip_code?: string;
  distance?: string;
  sort?: "date" | "price_asc" | "price_desc" | "distance";
}

export async function searchAds(filters: SearchFilters, offset: number, limit: number) {
  const { data, headers } = await get<BazosListItem[]>("ads.php", {
    section: SECTION_MOBILES,
    category: CATEGORY_APPLE,
    sort: filters.sort ?? "date",
    query: filters.query,
    price_from: filters.price_from,
    price_to: filters.price_to,
    zip_code: filters.zip_code?.replace(/\s/g, ""),
    distance: filters.zip_code ? filters.distance : undefined,
    offset,
    limit,
  });
  return { items: data ?? [], total: Number(headers.get("x-total-count") ?? 0) || (data?.length ?? 0) };
}

const detailCache = new TtlCache<BazosDetail>(10 * 60_000);

export async function getDetail(adId: string): Promise<BazosDetail> {
  const cached = detailCache.get(adId);
  if (cached) return cached;
  const { data } = await get<BazosDetail>("ad-detail-2.php", { ad_id: adId });
  detailCache.set(adId, data);
  return data;
}

export interface SellerReview {
  text: string;
  positive: boolean;
  author?: string;
  date?: string | null;
}

/** Recenze prodejce. Telefony a e-maily recenzentů záměrně zahazujeme. */
export async function getSellerReviews(phoneId: string): Promise<SellerReview[]> {
  const { data } = await get<{ message?: string; type?: string; name?: string; from?: string }[]>("ratings.php", {
    phone_id: phoneId,
    offset: 0,
    limit: 50,
  });
  return (data ?? []).map((r) => ({
    text: (r.message ?? "").trim(),
    positive: r.type === "positive",
    author: r.name,
    date: toIsoDate(r.from),
  }));
}

export interface SellerAd {
  id: string;
  title: string;
  price: number | null;
  url: string;
  image?: string;
  isPhone: boolean;
}

export async function getSellerAds(phoneId: string, limit = 40): Promise<SellerAd[]> {
  const { data } = await get<BazosListItem[]>("ads.php", { phone_id: phoneId, offset: 0, limit });
  return (data ?? []).map((a) => ({
    id: a.id,
    title: a.title,
    price: parsePrice(a.price_formatted),
    url: a.url,
    image: a.image_thumbnail,
    isPhone: a.url.includes("mobil.bazos."),
  }));
}

export function parsePrice(text?: string | null): number | null {
  if (!text || /dohodou|v textu|zdarma/i.test(text)) return null;
  const digits = text.replace(/\D/g, "");
  return digits ? Number(digits) : null;
}

/** API posílá RFC 2822 ("Tue, 06 Oct 2026 10:05:04 +0200") i "2026-10-06 10:03:02". */
export function toIsoDate(value?: string | null): string | null {
  if (!value) return null;
  const d = new Date(/^\d{4}-\d{2}-\d{2} /.test(value) ? value.replace(" ", "T") : value);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}
