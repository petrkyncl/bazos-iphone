import type { EvaluatedAd } from "@/lib/types";
import { formatAgo, formatCzk } from "@/lib/format";
import { DealDot, GradeChip, ProfitPill, Tag } from "./ui";

export function AdCard({ ad, selected, onSelect }: { ad: EvaluatedAd; selected: boolean; onSelect: () => void }) {
  const dimmed = ad.deal === "excluded";
  return (
    <button
      onClick={onSelect}
      className={`flex w-full gap-3 rounded-xl border bg-[var(--surface)] p-3 text-left shadow-[var(--shadow)] transition-colors hover:border-[var(--accent)] ${
        selected ? "border-[var(--accent)] ring-1 ring-[var(--accent)]" : ""
      } ${dimmed ? "opacity-55" : ""}`}
    >
      <div className="relative h-24 w-24 shrink-0 overflow-hidden rounded-lg bg-[var(--surface-2)] sm:h-28 sm:w-28">
        {ad.thumb && (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={ad.thumb} alt="" loading="lazy" className="h-full w-full object-cover" />
        )}
        {ad.isTop && (
          <span className="absolute left-1 top-1 rounded bg-amber-500 px-1 text-[10px] font-bold text-white">TOP</span>
        )}
      </div>

      <div className="flex min-w-0 flex-1 flex-col gap-1.5">
        <div className="flex items-start gap-2">
          <DealDot deal={ad.deal} />
          <h3 className="line-clamp-2 min-w-0 flex-1 text-sm font-medium leading-snug">{ad.title}</h3>
        </div>

        <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
          <span className="text-lg font-bold tabular-nums">{ad.price ? formatCzk(ad.price) : ad.priceText ?? "—"}</span>
          {ad.profit !== null && ad.deal !== "excluded" && <ProfitPill profit={ad.profit} />}
          {ad.buyout !== null && (
            <span className="text-xs text-[var(--text-subtle)]">výkup {formatCzk(ad.buyout)}</span>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-1.5">
          {ad.model && (
            <Tag>
              {ad.model}
              {ad.storageGb ? ` · ${ad.storageGb >= 1024 ? `${ad.storageGb / 1024} TB` : `${ad.storageGb} GB`}` : ""}
            </Tag>
          )}
          <GradeChip grade={ad.condition.grade} faded={ad.condition.confidence === "nizka"} />
          {ad.condition.batteryPct !== null && <Tag>🔋 {ad.condition.batteryPct} %</Tag>}
          {ad.issue && <Tag tone={ad.deal === "excluded" ? "bad" : "warn"}>{ad.issue.label}</Tag>}
          {ad.condition.warnings.slice(0, 1).map((w) => (
            <Tag key={w} tone="warn">
              ⚠ {w.split(" (")[0].split(" — ")[0]}
            </Tag>
          ))}
        </div>

        <div className="mt-auto flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-[var(--text-subtle)]">
          <span>{ad.locality}</span>
          <span>{formatAgo(ad.date)}</span>
          <span className="truncate">
            👤 {ad.seller.name ?? "?"} · {ad.seller.totalAds} {ad.seller.totalAds === 1 ? "inzerát" : "inz."}
            {ad.seller.reviewsCount > 0 && ` · ★ ${ad.seller.reviewsCount}`}
          </span>
        </div>
      </div>
    </button>
  );
}
