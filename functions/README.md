# HomeStock Cloud Functions

Backend for HomeStock's premium AI/data features — four callable functions, all gated on the
caller's household having an active Premium subscription (re-checked server-side, not just in
the app UI):

| Function | Used by | Calls |
| --- | --- | --- |
| `recognizeProduct` | AI-productherkenning (Voorraad scanner) | Claude API (Anthropic) |
| `recognizeReceipt` | Bonnetje scannen | Claude API (Anthropic) |
| `generateRecipe` | Recepten → "Genereer met AI" | Claude API (Anthropic) |
| `searchRecipes` / `getRecipeInformation` | Recepten (browse/zoek/detail) | Spoonacular API |

Two external API keys are involved, and **neither ever reaches the Android app** — they live
only in this project's Secret Manager config, which is the whole reason these go through a
backend instead of calling the APIs directly from the device (anyone could otherwise extract a
key straight from the APK).

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
# (free tier: 150 points/day, plenty for personal/household use; complexSearch and
# findByIngredients cost a handful of points per call, recipe detail costs ~1)
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

## Cost

**Claude Haiku 4.5** pricing: $1 / $5 per million input/output tokens.

- `recognizeProduct` (one product photo): ~1,600 image tokens + ~300 prompt tokens in, ~150 out
  — roughly **$0.003 per scan**.
- `recognizeReceipt` (one receipt photo): similar image cost, more output tokens for a full
  line-item list — roughly **$0.004–0.008 per scan** depending on receipt length.
- `generateRecipe` (text-only): a few hundred tokens each way — roughly **$0.001 per recipe**.

**Spoonacular**: free tier is 150 points/day. `complexSearch`/`findByIngredients` cost a handful
of points per call (more with `addRecipeInformation=true`, used for browse/search so opening a
result doesn't need a second call), `recipes/{id}/information` costs ~1 point. This comfortably
covers casual personal/household use; if a household browses recipes heavily, Spoonacular's paid
tiers start around $10/month for a much higher quota.

Cloud Functions itself has a generous free tier and every function's own compute cost is
negligible next to the external API calls.
