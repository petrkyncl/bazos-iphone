import type { NextRequest } from "next/server";
import { getPricelist } from "@/lib/mp";

/** Výkupní ceník iPhonů z mp.cz. `?refresh=1` vynutí nové stažení. */
export async function GET(req: NextRequest) {
  try {
    const list = await getPricelist(req.nextUrl.searchParams.get("refresh") === "1");
    return Response.json(list);
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 502 });
  }
}
