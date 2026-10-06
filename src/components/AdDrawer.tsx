"use client";

import { useEffect, useState } from "react";
import type { AdDetailResponse, EvaluatedAd, Grade } from "@/lib/types";
import { CONFIDENCE_NAMES, formatAgo, formatCzk, GRADE_NAMES, GRADE_ORDER } from "@/lib/format";
import { GradeChip, ProfitPill, Tag } from "./ui";

export function AdDrawer({ ad, onClose }: { ad: EvaluatedAd; onClose: () => void }) {
  const [detail, setDetail] = useState<{ id: string; data: AdDetailResponse | null; error?: string } | null>(null);
  const [override, setOverride] = useState<{ id: string; grade: Grade } | null>(null);
  const [photo, setPhoto] = useState<number | null>(null);

  useEffect(() => {
    let active = true;
    fetch(`/api/ads/${ad.id}`)
      .then((r) => r.json())
      .then((d) => active && setDetail({ id: ad.id, data: d.error ? null : d, error: d.error }))
      .catch((e) => active && setDetail({ id: ad.id, data: null, error: String(e) }));
    return () => {
      active = false;
    };
  }, [ad.id]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        if (photo !== null) setPhoto(null);
        else onClose();
      }
      if (photo !== null && e.key === "ArrowRight") setPhoto((photo + 1) % ad.images.length);
      if (photo !== null && e.key === "ArrowLeft") setPhoto((photo - 1 + ad.images.length) % ad.images.length);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [photo, ad.images.length, onClose]);

  const seller = detail?.id === ad.id ? detail.data?.seller : undefined;
  const loadingSeller = detail?.id !== ad.id;
  const grade = override?.id === ad.id ? override.grade : ad.condition.grade;
  const c = ad.condition;

  return (
    <div className="flex h-full flex-col overflow-y-auto bg-[var(--surface)]">
      {/* hlavička */}
      <div className="sticky top-0 z-10 flex items-start gap-3 border-b bg-[var(--surface)] p-4">
        <div className="min-w-0 flex-1">
          <h2 className="text-lg font-semibold leading-tight">{ad.title}</h2>
          <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-[var(--text-subtle)]">
            <span className="text-xl font-bold text-[var(--text)] tabular-nums">{ad.price ? formatCzk(ad.price) : ad.priceText}</span>
            <span>{ad.locality}</span>
            <span>{formatAgo(ad.date)}</span>
            <span>{ad.views.toLocaleString("cs-CZ")}× zobrazeno</span>
          </div>
        </div>
        <button onClick={onClose} className="rounded-lg p-1.5 text-[var(--text-subtle)] hover:bg-[var(--surface-2)]" aria-label="Zavřít">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M18 6 6 18M6 6l12 12" /></svg>
        </button>
      </div>

      <div className="flex flex-col gap-4 p-4">
        <div className="flex flex-wrap gap-2">
          <a href={ad.url} target="_blank" rel="noopener noreferrer" className="rounded-lg bg-[var(--accent)] px-3 py-1.5 text-sm font-medium text-white hover:bg-[var(--accent-hover)]">
            Otevřít na Bazoši ↗
          </a>
          {ad.lat !== null && ad.lng !== null && (
            <a href={`https://www.google.com/maps/search/?api=1&query=${ad.lat},${ad.lng}`} target="_blank" rel="noopener noreferrer" className="rounded-lg border px-3 py-1.5 text-sm font-medium hover:border-[var(--accent)]">
              Mapa ({ad.zip ?? ad.locality}) ↗
            </a>
          )}
        </div>

        {/* fotky */}
        {ad.images.length > 0 && (
          <div className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1">
            {ad.images.map((src, i) => (
              <button key={src} onClick={() => setPhoto(i)} className="h-36 w-36 shrink-0 overflow-hidden rounded-lg border bg-[var(--surface-2)]">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={src} alt={`Foto ${i + 1}`} loading="lazy" className="h-full w-full object-cover" />
              </button>
            ))}
          </div>
        )}

        {/* upozornění */}
        {(ad.issue || c.warnings.length > 0) && (
          <div className="flex flex-col gap-1.5 rounded-xl border border-[var(--warn)]/30 bg-[var(--warn-bg)] p-3 text-sm text-[var(--warn)]">
            {ad.issue && <div className="font-semibold">{ad.issue.label}</div>}
            {c.warnings.map((w) => (
              <div key={w}>⚠ {w}</div>
            ))}
          </div>
        )}

        {/* stav */}
        <section className="rounded-xl border p-4">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-sm font-semibold">Odhad stavu</h3>
            <GradeChip grade={c.grade} />
            <span className="text-xs text-[var(--text-subtle)]">{CONFIDENCE_NAMES[c.confidence]}</span>
            {c.batteryPct !== null && <Tag>🔋 baterie {c.batteryPct} %</Tag>}
          </div>
          {c.signals.length > 0 ? (
            <ul className="mt-2 flex flex-wrap gap-1.5">
              {c.signals.map((s) => (
                <li key={s.label} className="rounded-md bg-[var(--surface-2)] px-2 py-0.5 text-xs text-[var(--text-muted)]">
                  <span className="font-semibold">{s.grade}</span> {s.label} <span className="text-[var(--text-subtle)]">„{s.match}“</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="mt-2 text-xs text-[var(--text-subtle)]">Prodejce stav nepopsal — počítám konzervativně se stavem B.</p>
          )}
        </section>

        {/* výkup */}
        <section className="rounded-xl border p-4">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h3 className="text-sm font-semibold">
              Výkup mp.cz{ad.buyoutModel && <span className="font-normal text-[var(--text-subtle)]"> · {ad.buyoutModel.replace(/^Apple /, "")}</span>}
            </h3>
            {ad.buyoutUrl && (
              <a href={ad.buyoutUrl} target="_blank" rel="noopener noreferrer" className="text-xs text-[var(--accent)] hover:underline">
                na mp.cz ↗
              </a>
            )}
          </div>
          {ad.storageGuessed && ad.buyPrices && (
            <p className="mt-1 text-xs text-[var(--warn)]">Kapacita v inzerátu chybí nebo nesedí — použita {ad.storageGb ? "nejbližší" : "nejlevnější"} varianta.</p>
          )}
          {ad.buyPrices ? (
            <>
              <p className="mt-1 text-xs text-[var(--text-subtle)]">Klikni na stav, pokud podle fotek odhaduješ jiný.</p>
              <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-5">
                {GRADE_ORDER.map((g) => {
                  const buy = ad.buyPrices?.[g];
                  const active = g === grade;
                  return (
                    <button
                      key={g}
                      disabled={!buy}
                      onClick={() => setOverride({ id: ad.id, grade: g })}
                      className={`rounded-lg border p-2 text-left transition-colors disabled:opacity-40 ${
                        active ? "border-[var(--accent)] bg-[var(--accent-soft)]" : "bg-[var(--surface-2)] hover:border-[var(--accent)]"
                      }`}
                    >
                      <div className="text-xs text-[var(--text-subtle)]">
                        {g} {GRADE_NAMES[g]}
                      </div>
                      <div className="text-sm font-semibold tabular-nums">{buy ? formatCzk(buy) : "nevykupují"}</div>
                      {buy && ad.price ? (
                        <div className="mt-1">
                          <ProfitPill profit={buy - ad.price} />
                        </div>
                      ) : null}
                      {ad.sellPrices?.[g] && <div className="mt-1 text-[11px] text-[var(--text-subtle)]">prodávají za {formatCzk(ad.sellPrices[g])}</div>}
                    </button>
                  );
                })}
              </div>
            </>
          ) : (
            <p className="mt-2 text-sm text-[var(--text-subtle)]">{ad.model ? `Model ${ad.model} není ve výkupním ceníku.` : "Model se nepodařilo rozpoznat."}</p>
          )}
        </section>

        {/* prodejce */}
        <section className="rounded-xl border p-4">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-sm font-semibold">Prodejce</h3>
            <span className="text-sm">{ad.seller.name ?? "—"}</span>
            {seller?.likelyDealer && <Tag tone="warn">Pravděpodobně překupník ({seller.phoneAds} telefonů)</Tag>}
          </div>
          <div className="mt-2 grid grid-cols-3 gap-2 text-center">
            <Stat label="inzerátů" value={ad.seller.totalAds} />
            <Stat label="kladných" value={seller ? seller.positive : ad.seller.reviewsCount ? "…" : 0} tone="good" />
            <Stat label="záporných" value={seller ? seller.negative : ad.seller.reviewsCount ? "…" : 0} tone="bad" />
          </div>
          {loadingSeller && (ad.seller.reviewsCount > 0 || ad.seller.totalAds > 1) && (
            <p className="mt-3 text-xs text-[var(--text-subtle)]">Načítám recenze a další inzeráty…</p>
          )}
          {detail?.error && <p className="mt-3 text-xs text-[var(--bad)]">Profil prodejce se nepodařilo načíst: {detail.error}</p>}
          {seller && seller.reviews.length > 0 && (
            <div className="mt-3 flex flex-col gap-2">
              <h4 className="text-xs font-semibold uppercase tracking-wide text-[var(--text-subtle)]">Recenze</h4>
              {seller.reviews.map((r, i) => (
                <div key={i} className="rounded-lg bg-[var(--surface-2)] p-2.5 text-sm">
                  <div className="flex items-center gap-2 text-xs">
                    <span className={r.positive ? "text-[var(--good)]" : "text-[var(--bad)]"}>{r.positive ? "👍 kladná" : "👎 záporná"}</span>
                    <span className="text-[var(--text-subtle)]">
                      {r.author} · {r.date ? new Date(r.date).toLocaleDateString("cs-CZ") : ""}
                    </span>
                  </div>
                  {r.text && <p className="mt-1 text-[var(--text-muted)]">{r.text}</p>}
                </div>
              ))}
            </div>
          )}
          {seller && seller.otherAds.length > 0 && (
            <div className="mt-3 flex flex-col gap-1">
              <h4 className="text-xs font-semibold uppercase tracking-wide text-[var(--text-subtle)]">Další inzeráty prodejce</h4>
              {seller.otherAds.map((o) => (
                <a key={o.id} href={o.url} target="_blank" rel="noopener noreferrer" className="flex items-center gap-2 rounded-lg p-1.5 text-sm hover:bg-[var(--surface-2)]">
                  {o.image && (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={o.image} alt="" loading="lazy" className="h-9 w-9 shrink-0 rounded object-cover" />
                  )}
                  <span className="min-w-0 flex-1 truncate">
                    {o.isPhone && "📱 "}
                    {o.title}
                  </span>
                  <span className="shrink-0 tabular-nums text-[var(--text-subtle)]">{formatCzk(o.price)}</span>
                </a>
              ))}
            </div>
          )}
        </section>

        {/* popis */}
        <section className="rounded-xl border p-4">
          <h3 className="text-sm font-semibold">Popis</h3>
          <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-[var(--text-muted)]">{ad.description || "Bez popisu."}</p>
        </section>
      </div>

      {/* lightbox */}
      {photo !== null && (
        <div onClick={() => setPhoto(null)} className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 p-4">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src={ad.images[photo]} alt="" onClick={(e) => e.stopPropagation()} className="max-h-[90vh] max-w-full rounded-lg object-contain" />
          {ad.images.length > 1 && (
            <>
              <LightboxNav dir="prev" onClick={() => setPhoto((photo - 1 + ad.images.length) % ad.images.length)} />
              <LightboxNav dir="next" onClick={() => setPhoto((photo + 1) % ad.images.length)} />
              <span className="absolute bottom-4 rounded-full bg-black/60 px-3 py-1 text-xs text-white">
                {photo + 1} / {ad.images.length}
              </span>
            </>
          )}
        </div>
      )}
    </div>
  );
}

function Stat({ label, value, tone }: { label: string; value: React.ReactNode; tone?: "good" | "bad" }) {
  const color = tone === "good" ? "text-[var(--good)]" : tone === "bad" ? "text-[var(--bad)]" : "";
  return (
    <div className="rounded-lg bg-[var(--surface-2)] p-2">
      <div className={`text-lg font-bold tabular-nums ${color}`}>{value}</div>
      <div className="text-xs text-[var(--text-subtle)]">{label}</div>
    </div>
  );
}

function LightboxNav({ dir, onClick }: { dir: "prev" | "next"; onClick: () => void }) {
  return (
    <button
      onClick={(e) => {
        e.stopPropagation();
        onClick();
      }}
      className={`absolute top-1/2 -translate-y-1/2 rounded-full bg-white/15 p-3 text-white hover:bg-white/25 ${dir === "prev" ? "left-3" : "right-3"}`}
      aria-label={dir === "prev" ? "Předchozí" : "Další"}
    >
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d={dir === "prev" ? "m15 18-6-6 6-6" : "m9 18 6-6-6-6"} />
      </svg>
    </button>
  );
}
