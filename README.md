# Bazoš iPhony × výkup mp.cz

Web, který ukazuje iPhony z Bazoše a ke každému inzerátu přidá:

- **rozpoznaný model a kapacitu** (iPhone X až 18, SE, Air, 16e/17e…),
- **odhad stavu A+ až D** podle textu inzerátu — **pravidla, žádné AI** (zdarma, okamžitě),
- **výkupní cenu mp.cz** pro odhadnutý stav a **zisk** (výkup − cena inzerátu),
- **info o prodejci**: počet inzerátů, kladné/záporné recenze, jeho další inzeráty, příznak překupníka,
- šum: prodáno / rezervováno / poptávka / příslušenství / podezřele nízká cena, repliky, iCloud, vyměněné díly.

## Android aplikace (`android/`)

Nativní appka (Kotlin + Jetpack Compose, Material You) — samostatná, volá API Bazoše i mp.cz přímo
z telefonu, žádný server. Ceník mp.cz si drží v telefonu a obnovuje 1× denně.

```bash
cd android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:installDebug      # build + instalace do připojeného telefonu
./gradlew :app:testDebugUnitTest # testy parseru a pravidel stavu
```

Logika je port webové verze: `domain/Matching.kt`, `domain/Condition.kt` (pravidla stavu),
`data/BazosApi.kt`, `data/MpPricelist.kt`, `data/AdRepository.kt`; UI v `ui/`.

## Web — spuštění

```bash
npm install
npm run dev -- --port 3010
```

Pak otevři http://localhost:3010. První načtení stáhne výkupní ceník mp.cz (~10 s), uloží ho do
`data/mp-apple.json` a obnovuje ho nejvýš jednou denně.

## Jak to funguje

```
prohlížeč ──► /api/ads ─────► Bazoš mobilní API (ads.php + ad-detail-2.php)
                    │
                    ├──────► mp.cz výkupní API (ceník, cache na disku)
                    ▼
               evaluate.ts: model → ceník, text → stav, zisk, šum
prohlížeč ──► /api/ads/[id] ► + recenze a další inzeráty prodejce
```

| Soubor | Co dělá |
|---|---|
| `src/lib/bazos.ts` | klient mobilního API Bazoše (zjištěno dekompilací Android appky, viz `docs/bazos-api.md`) |
| `src/lib/mp.ts` | výkupní ceník iPhonů z mp.cz + výběr varianty podle kapacity |
| `src/lib/matching.ts` | parser názvu iPhonu, hledání frází s negací („bez škrábanců") |
| `src/lib/condition.ts` | pravidla stavu A+–D, baterie, varování, šum v inzerátech |
| `src/lib/evaluate.ts` | spojí vše dohromady pro jeden inzerát |
| `src/components/` | UI: filtry, karty, detail |

Ladění pravidel stavu: `src/lib/condition.ts` → pole `RULES` (regexy nad textem bez diakritiky).
V detailu inzerátu jde stav přepnout ručně — zisk se ukáže pro všechny stavy.

## Pozor

Obě API jsou **neoficiální** a můžou se bez varování změnit. Aplikace je šetrná: max 6 souběžných
požadavků na Bazoš, detaily inzerátů v cache 10 min, ceník mp.cz 1× denně.
