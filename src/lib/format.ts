import type { Grade } from "./types";

const czk = new Intl.NumberFormat("cs-CZ", { maximumFractionDigits: 0 });

export function formatCzk(n: number | null | undefined): string {
  return n == null ? "—" : `${czk.format(n)} Kč`;
}

export function formatProfit(n: number): string {
  return `${n > 0 ? "+" : n < 0 ? "−" : ""}${czk.format(Math.abs(n))} Kč`;
}

export function formatAgo(iso: string | null): string {
  if (!iso) return "";
  const d = new Date(iso);
  const mins = Math.round((Date.now() - d.getTime()) / 60_000);
  if (mins < 1) return "právě teď";
  if (mins < 60) return `před ${mins} min`;
  const h = Math.round(mins / 60);
  if (h < 24) return `před ${h} h`;
  const days = Math.round(h / 24);
  if (days < 7) return `před ${days} ${days === 1 ? "dnem" : "dny"}`;
  return d.toLocaleDateString("cs-CZ", { day: "numeric", month: "numeric", year: "numeric" });
}

export const GRADE_ORDER: Grade[] = ["A+", "A", "B", "C", "D"];

export const GRADE_NAMES: Record<Grade, string> = {
  "A+": "Zánovní",
  A: "Jako nový",
  B: "Použitý",
  C: "Opotřebovaný",
  D: "Poškozený",
};

export const CONFIDENCE_NAMES: Record<string, string> = {
  vysoka: "vysoká jistota",
  stredni: "střední jistota",
  nizka: "stav nepopsán",
};
