# Bazoš mobile API (reverse-engineered z Android app 2.26.1, `cz.ackee.bazos`)

Zdroj: dekompilace jadx (`ti/o.java` = repository inzerátů, `oj/s.java` = Ktor HTTP klient).
Ověřeno živými requesty 2026-10-06.

## Základ

- Base URL: `https://www.bazos.cz/` (stejné API běží na `bazos.sk`, `bazos.pl`, `bazos.at`)
- Všechny endpointy: `api/v2/<jméno>.php`
- **Čtecí endpointy nepotřebují žádnou autentizaci ani speciální hlavičky** (app posílá jen Ktor default UA).
- Odpovědi jsou JSON, většina hodnot je string (`"topped":"true"`, `"views":"88"`).
- Stránkování: `offset` + `limit`, celkový počet v hlavičce `x-total-count`.

## Čtecí endpointy (ověřené)

### `GET api/v2/sections.php`
Seznam rubrik: `{id, title, url, image}`. Mobily = `MO`, PC = `PC`, Elektro = `EL`.

### `GET api/v2/categories.php?section=MO`
Kategorie v rubrice. Mobily: Apple = `439`, Samsung = `346`, Google = `440`, Xiaomi = `455`, Chytré hodinky = `454`.

### `GET api/v2/ads.php` — výpis / hledání
| param | význam |
|---|---|
| `offset`, `limit` | stránkování (app používá limit 20) |
| `query` | fulltext |
| `section` | ID rubriky (`MO`) |
| `category` | ID kategorie (`439` = Apple) |
| `price_from`, `price_to` | cena v Kč |
| `zip_code`, `distance` | PSČ bez mezery + okruh v km |
| `latitude`, `longitude` | alternativa k PSČ |
| `type` | `sell` / `rent` |
| `sort` | `date`, `price_asc`, `price_desc`, `distance` |
| `agent_id` | výsledky uloženého hlídacího psa |
| `similar_ad_id` | podobné inzeráty k danému ID |

Položka: `id, from, title, price_formatted, currency, image_thumbnail, locality, topped, favourite, url, views`.
Pozn.: topované inzeráty jsou vždy nahoře bez ohledu na `sort`. `from` u výpisu neodpovídá datu vložení.

### `GET api/v2/ad-detail-2.php?ad_id=<id>`
Plný detail: `description`, `price` (číslo), `price_type` (`EXACT`, …), `status`, `images[]` (plné rozlišení),
`locality`, `zip_code`, `latitude/longitude`, `category`, `section`, `views`, `name`,
`user_ads_count`, `user_reviews_count`, `from` (datum). Telefon je zkrácený (`"730"`), celý jde přes `ad-phone.php`.

### `GET api/v2/ratings.php?phone_id=<phone_id>&offset=0&limit=50`
Recenze prodejce (`phone_id` je v detailu inzerátu): `{name, message, type: "positive"|"negative", from}`.
Pozn.: odpověď obsahuje i telefon/e-mail recenzenta — neukládat.

### `GET api/v2/ads.php?phone_id=<phone_id>`
Všechny inzeráty prodejce (napříč rubrikami) — stejné jako „Všechny inzeráty uživatele“ na webu.

### Další (nalezené v kódu, neověřené)
`suggestionsnew.php` (našeptávač), `zip.php` (PSČ), `deleted-ads.php`.

## Endpointy s účtem / zápisem (nepoužíváme)
`ad-insert`, `ad-edit`, `ad-remove`, `ad-top`, `ad-phone`, `ad-mail`, `favourite-*`, `agent-*` (hlídací psi),
`confirm-phone*`, `image-upload`, `notifications`, `guid`, `bank-acccount-verification`.

## Fair use
Neoficiální API → rozumné tempo (≤ 1 req/s), cache detailů, žádné hromadné stahování.
