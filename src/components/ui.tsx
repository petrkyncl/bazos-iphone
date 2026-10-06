import type { Deal, Grade } from "@/lib/types";
import { formatProfit, GRADE_NAMES } from "@/lib/format";

const GRADE_STYLE: Record<Grade, string> = {
  "A+": "bg-[var(--good-bg)] text-[var(--good)]",
  A: "bg-[var(--good-bg)] text-[var(--good)]",
  B: "bg-[var(--info-bg)] text-[var(--info)]",
  C: "bg-[var(--warn-bg)] text-[var(--warn)]",
  D: "bg-[var(--bad-bg)] text-[var(--bad)]",
};

export function GradeChip({ grade, faded = false }: { grade: Grade; faded?: boolean }) {
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-xs font-semibold ${GRADE_STYLE[grade]} ${faded ? "opacity-60" : ""}`}
      title={GRADE_NAMES[grade]}
    >
      {grade}
      <span className="font-normal">{GRADE_NAMES[grade]}</span>
    </span>
  );
}

export function ProfitPill({ profit, size = "sm" }: { profit: number; size?: "sm" | "lg" }) {
  const cls = profit > 0 ? "bg-[var(--good-bg)] text-[var(--good)]" : "bg-[var(--bad-bg)] text-[var(--bad)]";
  return (
    <span className={`inline-flex rounded-md font-semibold tabular-nums ${cls} ${size === "lg" ? "px-2.5 py-1 text-base" : "px-1.5 py-0.5 text-xs"}`}>
      {formatProfit(profit)}
    </span>
  );
}

export const DEAL_LABEL: Record<Deal, string> = {
  good: "Výhodné",
  bad: "Nevýhodné",
  suspicious: "Podezřelé",
  unknown: "Neznámé",
  excluded: "Vyřazeno",
};

export function DealDot({ deal }: { deal: Deal }) {
  const color = {
    good: "bg-[var(--good)]",
    bad: "bg-[var(--bad)]",
    suspicious: "bg-[var(--warn)]",
    unknown: "bg-[var(--muted)]",
    excluded: "bg-[var(--muted)]",
  }[deal];
  return <span className={`inline-block h-2 w-2 shrink-0 rounded-full ${color}`} title={DEAL_LABEL[deal]} />;
}

export function Tag({ children, tone = "neutral" }: { children: React.ReactNode; tone?: "neutral" | "warn" | "bad" }) {
  const cls = {
    neutral: "bg-[var(--surface-2)] text-[var(--text-muted)]",
    warn: "bg-[var(--warn-bg)] text-[var(--warn)]",
    bad: "bg-[var(--bad-bg)] text-[var(--bad)]",
  }[tone];
  return <span className={`inline-flex items-center rounded-md px-1.5 py-0.5 text-xs ${cls}`}>{children}</span>;
}
