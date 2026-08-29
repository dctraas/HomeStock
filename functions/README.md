# HomeStock Cloud Functions

Backend for HomeStock's premium AI/data features — callable functions, all gated on the
caller's household having an active Premium subscription (re-checked server-side, not just in
the app UI):

| Function | Used by | Calls |
| --- | --- | --- |
| `recognizeProduct` | AI-productherkenning (Voorraad scanner) | Claude API (Anthropic) |
| `recognizeReceipt` | Bonnetje scannen | Claude API (Anthropic) |
| `generateRecipe` | Recepten → "Genereer met AI" | Claude API (Anthropic) |
| `searchRecipes` / `getRecipeInformation` | Recepten (browse/zoek/detail) | Spoonacular API |
| `translateRecipe` | Recepten (titels + detail, wanneer app-taal ≠ Engels) | Claude API (Anthropic) |
| `importRecipeFromUrl` | Recepten → "Importeer van URL" | schema.org JSON-LD (page fetch, no API cost) when present, Claude API (Anthropic) as fallback |

Two external API keys are involved, and **neither ever reaches the Android app** — they live
only in this project's Secret Manager config, which is the whole reason these go through a
backend instead of calling the APIs directly from the device (anyone could otherwise extract a
key straight from the APK).

Alongside those, three Firestore-*triggered* functions (not callables — no API key involved)
push real-time cross-device notifications via FCM:

| Function | Fires on | Notifies |
| --- | --- | --- |
| `notifyHouseholdActivity` | new `households/{id}/activityLog/{entryId}` doc | rest of the household — "huisgenoot-activiteit" |
| `notifyHouseholdMemberJoined` | new `households/{id}/members/{uid}` doc | rest of the household — "huishouden-wijziging" |
| `notifyHouseholdMemberLeft` | deleted `households/{id}/members/{uid}` doc | rest of the household — "huishouden-wijziging" |

No extra setup needed for these beyond the normal deploy below — `admin.messaging()` uses this
project's own Firebase service account, the same one `admin.initializeApp()` already picked up.
See `HomeStockMessagingService.kt` on the Android side for how the pushes are received/displayed,
and `HouseholdMembersRepository.updateFcmToken` for how each member doc's `fcmToken` gets there.

## One-time setup

Requires the [Firebase CLI](https://firebase.google.com/docs/cli) and a **Blaze (pay-as-you-go)**
Firebase project — Cloud Functions cannot run on the free Spark plan, and outbound network
calls (to Anthropic/Spoonacular) require Blaze regardless of function usage.

```sh
npm install -g firebase-tools   # if you don't have it yet
firebase login
firebase use --add               # pick your HomeStock Firebase project, alias e.g. "default"
```

Install function dependencies:

```sh
cd functions
npm install
```

Store both API keys as Firebase secrets (never committed, never in the client):

```sh
firebase functions:secrets:set ANTHROPIC_API_KEY
# paste your Claude API key (sk-ant-...) when prompted — console.anthropic.com

firebase functions:secrets:set SPOONACULAR_API_KEY
# paste your Spoonacular key when prompted — sign up at spoonacular.com/food-api
# Free-tier daily point budget varies by when the key was issued (older keys got 150
# points/day; keys issued more recently are commonly capped at 50/day instead — check
# spoonacular.com/food-api/console for your key's actual limit). See the Cost section
# below for what that budget actually buys.
```

## Deploy

```sh
cd functions
npm run deploy
# equivalent to: npm run build && firebase deploy --only functions
```

All functions deploy to `europe-west1` (see `setGlobalOptions` in `src/index.ts` — change if
your Firestore is in a different region; keeping Functions and Firestore in the same region
avoids cross-region latency on every call).

## Unit tests

```sh
cd functions
npm test
```

Runs the pure, I/O-free helpers exported from `src/index.ts` (receipt price parsing, the
`analyzedInstructions` fallback that fixed the missing-instructions bug, cache-key building,
etc. — see `src/index.test.ts`) via [Vitest](https://vitest.dev). The `onCall` handlers
themselves aren't covered here — they talk to Firestore/Anthropic/Spoonacular, so exercising
them for real means the emulator below, not a unit test.

## Local testing (emulator)

```sh
cd functions
npm run serve
```

The emulator prompts for both secrets' values locally (or set them via a `.env.local` file in
this directory — see the
[Firebase secrets + emulator docs](https://firebase.google.com/docs/functions/config-env#local-secret-access)).

## What it does, and doesn't, protect against

- **API key exposure**: fully solved — both keys live only in Secret Manager, never reach the
  client.
- **Premium gating**: enforced server-side, not just in the Android UI — every function reads
  `households/{id}/members/*` from Firestore itself and rejects the call
  (`permission-denied`, `premium_required`) if no member of the caller's household has an
  active subscription. A modified APK cannot bypass this by skipping the client-side check.
- **Abuse / cost overrun**: only lightly protected right now — a 6 MB request-size cap on the
  photo-taking functions and Cloud Functions' own `maxInstances: 10` are the only limits. A
  premium household could still call these many times in a row and run up Anthropic/Spoonacular
  spend. If that becomes a concern, consider adding:
  - [Firebase App Check](https://firebase.google.com/docs/app-check) (Play Integrity provider)
    so only your real, unmodified app can call these functions at all — currently **not**
    configured here.
  - A simple per-household daily counter in Firestore, checked before the external API call.
- **`importRecipeFromUrl`'s outbound fetch (SSRF)**: this is the one function that fetches a URL
  the client supplies, so `isDisallowedImportHost` rejects the obvious cases (localhost, private
  IPv4 ranges, the cloud metadata IP) by hostname text before fetching. That's a text check, not
  network-level egress control — a DNS-rebinding attack could still slip a private address past
  it. Fine for this app's threat model (a private household tool, not a public multi-tenant
  target), but worth hardening further (e.g. resolve-then-check-IP, or route through an egress
  proxy) if this ever needs to withstand a more adversarial audience.

## Caching

Spoonacular recipe content and its Claude translations are identical for every household that
looks at the same recipe/locale — a photo scan or a freeform AI recipe is inherently per-call
and can't be cached, but "recipe #12345" and "recipe #12345 translated into Dutch" are the same
answer no matter who asks. Three Firestore collections cache these **across households**
(top-level, not nested under `households/{id}` — see `firestore.rules`, which denies the client
any direct access; only these functions, via the Admin SDK, ever touch them):

| Collection | Written by | TTL | What it saves |
| --- | --- | --- | --- |
| `recipeDetailCache/{spoonacularId}` | `searchRecipes` (backfill), `getRecipeInformation` | 30 days | A Spoonacular `recipes/{id}/information` call, the second+ time *any* household opens that recipe. |
| `recipeSearchCache/{browseParams}` | `searchRecipes` ("browse" mode only) | 24 hours | A full `complexSearch` call for the filterless "browse popular recipes" list every Recepten screen opens with — by far the most repeated query. Keyed per 100-recipe chunk (`offset` is part of the cache key); the plain (no cuisine, no query, no `maxReadyTime`/`type`/`diet`) browse fetches and caches one chunk at a time, lazily, only when that particular chunk's cache entry is missing or stale — so "load more" paging past chunk 1 costs a live call only the first time anyone reaches that far in a given 24h window. (An earlier version eagerly pre-fetched all nine chunks — the whole ~900-recipe catalog Spoonacular's own offset cap allows — the moment any one of them went stale; that cost ~63 points in a single call, which alone exceeds a 50-point/day key's entire daily budget. See `fetchAndCacheBrowseChunk`'s doc comment in `src/index.ts`.) The much smaller cuisine-boosted call (page 1 only, 8 recipes) — and any "browse" call that *does* carry the Recepten filter sheet's `maxReadyTime`/`type`/`diet` — also cache lazily, one page at a time, each combination getting its own `browseCacheKey` entry (see that function's doc). |
| `recipeTranslations/{spoonacularId}_{locale}` | `translateRecipe` | 90 days | A Claude translation call, the second+ time *any* household opens/lists that recipe in that language. AI-generated and hand-entered recipes are deliberately excluded (private, household-specific content — see `isCacheableRecipeId`). |

All three are best-effort: a cache read/write failure is logged and swallowed, never thrown —
worst case a call falls back to a live Spoonacular/Claude request instead of failing outright.

Open Food Facts barcode lookups and free-text searches get the same cross-household treatment,
just client-side (`products`/`productSearchCache` — see `ProductRepository.kt`) since OFF needs
no secret key and is already called directly from the app.

## Cost

**Claude Haiku 4.5** pricing: $1 / $5 per million input/output tokens.

- `recognizeProduct` (one product photo): ~1,600 image tokens + ~300 prompt tokens in, ~150 out
  — roughly **$0.003 per scan**.
- `recognizeReceipt` (one receipt photo): similar image cost, more output tokens for a full
  line-item list — roughly **$0.004–0.008 per scan** depending on receipt length.
- `generateRecipe` (text-only): a few hundred tokens each way — roughly **$0.001 per recipe**.
- `translateRecipe` (text-only): "titles" mode is a batch of short strings (~$0.0005 for a
  24-recipe list); "detail" mode is one recipe's full text — roughly **$0.001–0.002 per recipe**.
  With the `recipeTranslations` cache above, this is spent once per (recipe, locale) pair across
  **all** households, not once per household — the more overlap in what people browse (likely
  high, since "browse" itself is now also cached and shared), the closer actual spend gets to
  that one-time cost regardless of user count.
- `importRecipeFromUrl`: **free** (no Anthropic call at all) whenever the page already carries
  schema.org Recipe JSON-LD — true for most recipe sites, which embed it for Google's own recipe
  rich-results. Only falls back to Claude (full page text in, one recipe out — similar token
  cost to `generateRecipe`, roughly **$0.001–0.002 per import**) when a page has no usable
  JSON-LD.

**Spoonacular**: free-tier daily point budget is **50 or 150 points/day depending on the key**
(Spoonacular has shrunk the free tier over time — check spoonacular.com/food-api/console for what
a given key actually has). The exact per-call cost, from Spoonacular's own pricing:

- `complexSearch`/`findByIngredients` base cost: 1 point, + 0.01 point per recipe returned.
- `addRecipeInformation=true` (used everywhere search/browse results need full detail so opening
  one doesn't need a second call): +0.025 point per recipe returned.
- `addRecipeNutrition=true`/`includeNutrition=true` (used everywhere full detail is fetched so
  RecipeDetailScreen can show per-serving calories/macros; also auto-enables
  `addRecipeInformation`): +0.025 point per recipe returned.
- `recipes/{id}/information`: ~1 point, plus the same nutrition surcharge.

So a single `complexSearch` call for 100 recipes with both flags on costs `1 + 100 × (0.01 + 0.025
+ 0.025)` ≈ **7 points** — and the browse-cache warm-up used to eagerly chain nine of those
(fetching the whole ~900-recipe catalog up front) for ≈63 points in one shot, comfortably
exceeding even the smaller key's entire daily budget in a single call. `searchRecipes` now fetches
one 100-recipe chunk at a time, lazily, only for the chunk actually requested (see the Caching
section above and `fetchAndCacheBrowseChunk`'s doc comment in `src/index.ts`), so a cache-cold
household's first "Recepten" open costs ~7 points instead of ~63. Combined with the 24h browse
cache, this is what determines how many active households (and how many distinct
allergen/intolerance filter combos among them — each combo gets its own cache entry) a given daily
budget can support before needing a paid plan (starting around $10/month for a much higher quota).
On a 50-point/day key, a handful of active households sharing the default (no-filter) browse cache
should fit comfortably; several households each with a *different* intolerance combo, or heavy
`query`/`ingredients`-mode searching (neither of those is cached per-result the way plain browse
is), can still exhaust the day faster — watch for `"Spoonacular quota/rate limit hit"` in the
Cloud Functions logs (`spoonacularGet`'s warning) to tell which case is happening.

Cloud Functions itself has a generous free tier and every function's own compute cost is
negligible next to the external API calls.
