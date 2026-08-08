# HomeStock Cloud Functions

Backend for the premium **AI-productherkenning** feature: a single callable function,
`recognizeProduct`, that receives a photo from the app, calls the Claude API (Anthropic) to
identify the product, and returns candidate names + categories. This exists so the Anthropic
API key never has to live inside the Android app (where anyone could extract it from the APK).

## One-time setup

Requires the [Firebase CLI](https://firebase.google.com/docs/cli) and a **Blaze (pay-as-you-go)**
Firebase project — Cloud Functions cannot run on the free Spark plan, and outbound network
calls (to the Anthropic API) require Blaze regardless of function usage.

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

Store your Anthropic API key as a Firebase secret (never committed, never in the client):

```sh
firebase functions:secrets:set ANTHROPIC_API_KEY
# paste your key (sk-ant-...) when prompted
```

## Deploy

```sh
cd functions
npm run deploy
# equivalent to: npm run build && firebase deploy --only functions
```

The function deploys to `europe-west1` (see `setGlobalOptions` in `src/index.ts` — change if
your Firestore is in a different region; keeping Functions and Firestore in the same region
avoids cross-region latency on every call).

## Local testing (emulator)

```sh
cd functions
npm run serve
```

The emulator prompts for the `ANTHROPIC_API_KEY` secret's value locally (or set it via a
`.env.local` file in this directory — see the
[Firebase secrets + emulator docs](https://firebase.google.com/docs/functions/config-env#local-secret-access)).

## What it does, and doesn't, protect against

- **API key exposure**: fully solved — the key lives only in Secret Manager, never reaches the
  client.
- **Premium gating**: enforced server-side, not just in the Android UI — the function reads
  `households/{id}/members/*` from Firestore itself and rejects the call
  (`permission-denied`, `premium_required`) if no member of the caller's household has an
  active subscription. A modified APK cannot bypass this by skipping the client-side check.
- **Abuse / cost overrun**: only lightly protected right now — a 6 MB request-size cap and
  Cloud Functions' own `maxInstances: 10` are the only limits. A premium household could still
  call this function many times in a row and run up Anthropic spend. If that becomes a
  concern, consider adding:
  - [Firebase App Check](https://firebase.google.com/docs/app-check) (Play Integrity provider)
    so only your real, unmodified app can call the function at all — currently **not**
    configured here.
  - A simple per-household daily counter in Firestore, checked before the Anthropic call.

## Cost

Claude Haiku 4.5 pricing: $1 / $5 per million input/output tokens. A single product photo
(~1,600 image tokens + ~300 prompt tokens in, ~150 tokens out) costs roughly **$0.003 per
scan** (~0.3 cent). Cloud Functions itself has a generous free tier and this function's own
compute cost is negligible next to the Anthropic call.
