"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import type { AdsResponse, EvaluatedAd } from "@/lib/types";
import { formatAgo } from "@/lib/format";
import { AdCard } from "./AdCard";
import { AdDrawer } from "./AdDrawer";

interface Filters {
  query: string;
  priceFrom: string;
  priceTo: string;
  zip: string;
  distance: string;
  sort: "date" | "profit" | "price_asc" | "price_desc";
  onlyGood: boolean;
  hideExcluded: boolean;
}

const DEFAULT_FILTERS: Filters = {
  query: "",
  priceFrom: "2000",
  priceTo: "",
  zip: "",
  distance: "25",
  sort: "date",
  onlyGood: false,
  hideExcluded: true,
};
const STORAGE_KEY = "bazos-iphone-filters";

function loadFilters(): Filters {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    return saved ? { ...DEFAULT_FILTERS, ...JSON.parse(saved) } : DEFAULT_FILTERS;
  } catch {
    return DEFAULT_FILTERS;
  }
}

const inputCls =
  "w-full rounded-lg border bg-[var(--surface)] px-3 py-2 text-sm outline-none focus:border-[var(--accent)] focus:ring-1 focus:ring-[var(--accent)]";

export function App() {
  const [filters, setFilters] = useState<Filters>(DEFAULT_FILTERS);
  const [ads, setAds] = useState<EvaluatedAd[]>([]);
  const [total, setTotal] = useState(0);
  const [nextOffset, setNextOffset] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pricelistAt, setPricelistAt] = useState<string | null>(null);
  const [selected, setSelected] = useState<EvaluatedAd | null>(null);

  const load = useCallback(async (f: Filters, offset: number) => {
    setLoading(true);
    setError(null);
    const qs = new URLSearchParams({ offset: String(offset) });
    if (f.query.trim()) qs.set("query", f.query.trim());
    if (f.priceFrom) qs.set("price_from", f.priceFrom);
    if (f.priceTo) qs.set("price_to", f.priceTo);
    if (f.zip.trim()) {
      qs.set("zip", f.zip.trim());
      qs.set("distance", f.distance);
    }
    if (f.sort === "price_asc" || f.sort === "price_desc") qs.set("sort", f.sort);
    try {
      const res = await fetch(`/api/ads?${qs}`);
      const data: AdsResponse & { error?: string } = await res.json();
      if (data.error) throw new Error(data.error);
      setAds((prev) => {
        const merged = offset === 0 ? data.ads : [...prev, ...data.ads];
        return merged.filter((a, i) => merged.findIndex((b) => b.id === a.id) === i);
      });
      setTotal(data.total);
      setNextOffset(offset + 30);
      setPricelistAt(data.pricelistUpdatedAt);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const f = loadFilters();
    setFilters(f); // eslint-disable-line react-hooks/set-state-in-effect -- obnova z localStorage až po hydrataci
    load(f, 0);
  }, [load]);

  const search = (e?: React.FormEvent) => {
    e?.preventDefault();
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(filters));
    } catch {}
    setSelected(null);
    load(filters, 0);
  };

  const set = <K extends keyof Filters>(k: K, v: Filters[K]) => setFilters((f) => ({ ...f, [k]: v }));
  const setAndSave = <K extends keyof Filters>(k: K, v: Filters[K]) => {
    const next = { ...filters, [k]: v };
    setFilters(next);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    } catch {}
    return next;
  };

  const visible = useMemo(() => {
    let list = ads;
    if (filters.hideExcluded) list = list.filter((a) => a.deal !== "excluded");
    if (filters.onlyGood) list = list.filter((a) => a.deal === "good");
    if (filters.sort === "profit") list = [...list].sort((a, b) => (b.profit ?? -Infinity) - (a.profit ?? -Infinity));
    return list;
  }, [ads, filters.hideExcluded, filters.onlyGood, filters.sort]);

  const goodCount = ads.filter((a) => a.deal === "good").length;

  return (
    <div className="mx-auto flex min-h-screen max-w-[1600px] flex-col">
      <header className="flex flex-wrap items-baseline justify-between gap-2 px-4 pb-2 pt-5 sm:px-6">
        <h1 className="text-xl font-bold tracking-tight">
          Bazoš iPhony <span className="font-normal text-[var(--text-subtle)]">× výkup mp.cz</span>
        </h1>
        <p className="text-xs text-[var(--text-subtle)]">
          {total > 0 && `${total.toLocaleString("cs-CZ")} inzerátů · načteno ${ads.length} · výhodných ${goodCount}`}
          {pricelistAt && ` · ceník ${formatAgo(pricelistAt)}`}
        </p>
      </header>

      <form onSubmit={search} className="mx-4 grid grid-cols-2 items-end gap-2 rounded-xl sm:flex sm:flex-wrap border bg-[var(--surface)] p-3 shadow-[var(--shadow)] sm:mx-6">
        <label className="col-span-2 sm:min-w-[12rem] sm:flex-[2_1_14rem]">
          <span className="mb-1 block text-xs text-[var(--text-subtle)]">Hledat</span>
          <input className={inputCls} value={filters.query} onChange={(e) => set("query", e.target.value)} placeholder="iphone 15 pro, 13 mini…" />
        </label>
        <label className="sm:w-24 sm:flex-1">
          <span className="mb-1 block text-xs text-[var(--text-subtle)]">Cena od</span>
          <input className={inputCls} inputMode="numeric" value={filters.priceFrom} onChange={(e) => set("priceFrom", e.target.value.replace(/\D/g, ""))} />
        </label>
        <label className="sm:w-24 sm:flex-1">
          <span className="mb-1 block text-xs text-[var(--text-subtle)]">Cena do</span>
          <input className={inputCls} inputMode="numeric" value={filters.priceTo} onChange={(e) => set("priceTo", e.target.value.replace(/\D/g, ""))} />
        </label>
        <label className="sm:w-24 sm:flex-1">
          <span className="mb-1 block text-xs text-[var(--text-subtle)]">PSČ</span>
          <input className={inputCls} inputMode="numeric" value={filters.zip} onChange={(e) => set("zip", e.target.value)} placeholder="celá ČR" />
        </label>
        <label className="sm:w-24 sm:flex-1">
          <span className="mb-1 block text-xs text-[var(--text-subtle)]">Okruh</span>
          <select className={inputCls} value={filters.distance} onChange={(e) => set("distance", e.target.value)} disabled={!filters.zip.trim()}>
            {["5", "10", "25", "50", "100"].map((d) => (
              <option key={d} value={d}>
                {d} km
              </option>
            ))}
          </select>
        </label>
        <button type="submit" disabled={loading} className="col-span-2 h-[38px] rounded-lg bg-[var(--accent)] px-4 text-sm font-semibold text-white hover:bg-[var(--accent-hover)] disabled:opacity-60">
          {loading && nextOffset === 0 ? "Hledám…" : "Hledat"}
        </button>
      </form>

      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3 text-sm sm:px-6">
        <label className="flex items-center gap-1.5">
          Řadit:
          <select
            className="rounded-md border bg-[var(--surface)] px-2 py-1 text-sm"
            value={filters.sort}
            onChange={(e) => {
              const next = setAndSave("sort", e.target.value as Filters["sort"]);
              // nejnovější/cena řadí Bazoš (nové hledání), zisk řadíme tady z načtených
              if (next.sort !== "profit") load(next, 0);
            }}
          >
            <option value="date">nejnovější</option>
            <option value="profit">největší zisk (z načtených)</option>
            <option value="price_asc">nejlevnější</option>
            <option value="price_desc">nejdražší</option>
          </select>
        </label>
        <label className="flex items-center gap-1.5">
          <input type="checkbox" checked={filters.onlyGood} onChange={(e) => setAndSave("onlyGood", e.target.checked)} />
          jen výhodné
        </label>
        <label className="flex items-center gap-1.5">
          <input type="checkbox" checked={filters.hideExcluded} onChange={(e) => setAndSave("hideExcluded", e.target.checked)} />
          skrýt prodané, poptávky a příslušenství
        </label>
      </div>

      {error && <div className="mx-4 rounded-lg bg-[var(--bad-bg)] p-3 text-sm text-[var(--bad)] sm:mx-6">Chyba: {error}</div>}

      <main className="flex flex-1 gap-4 px-4 pb-8 sm:px-6">
        <div className="flex min-w-0 flex-1 flex-col gap-2">
          {visible.map((ad) => (
            <AdCard key={ad.id} ad={ad} selected={selected?.id === ad.id} onSelect={() => setSelected(ad)} />
          ))}
          {!loading && ads.length > 0 && visible.length === 0 && (
            <p className="py-8 text-center text-sm text-[var(--text-subtle)]">V načtených inzerátech nic neodpovídá filtru — načti další.</p>
          )}
          {!loading && !error && ads.length === 0 && <p className="py-8 text-center text-sm text-[var(--text-subtle)]">Nic nenalezeno.</p>}
          {ads.length > 0 && nextOffset < total && (
            <button
              onClick={() => load(filters, nextOffset)}
              disabled={loading}
              className="mt-2 rounded-xl border bg-[var(--surface)] py-3 text-sm font-medium hover:border-[var(--accent)] disabled:opacity-60"
            >
              {loading ? "Načítám…" : `Načíst dalších 30 (zbývá ${(total - nextOffset).toLocaleString("cs-CZ")})`}
            </button>
          )}
          {loading && ads.length === 0 && <p className="py-8 text-center text-sm text-[var(--text-subtle)]">Načítám inzeráty a výkupní ceny…</p>}
        </div>

        {selected && (
          <aside className="fixed inset-0 z-40 lg:sticky lg:inset-auto lg:top-4 lg:z-auto lg:h-[calc(100vh-2rem)] lg:w-[520px] lg:shrink-0 xl:w-[600px]">
            <div className="h-full overflow-hidden lg:rounded-xl lg:border lg:shadow-[var(--shadow)]">
              <AdDrawer key={selected.id} ad={selected} onClose={() => setSelected(null)} />
            </div>
          </aside>
        )}
      </main>
    </div>
  );
}
