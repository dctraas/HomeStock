# HomeStock

Android-app om samen met je huisgenoten bij te houden welke boodschappen je nog in huis hebt.

## Functionaliteit

- **Delen met huisgenoten** — voorraad, boodschappenlijst en activiteit worden live gedeeld
  binnen een "huishouden": maak er een aan of sluit aan met een 6-tekens code.
- **Barcode scannen** — CameraX + ML Kit Barcode Scanning, volledig on-device (geen API-key nodig).
- **Productgegevens ophalen** — via de gratis, sleutelloze [Open Food Facts](https://world.openfoodfacts.org/) API; resultaten worden in het huishouden gecachet zodat iedereen ervan profiteert. Onbekende barcodes kun je handmatig invullen.
- **Voorraadoverzicht per categorie** — gegroepeerd met sticky headers (Zuivel, Groente & Fruit, Vlees & Vis, Dranken, …), categorie wordt automatisch geraden op basis van de Open Food Facts data.
- **Toevoegen/verwijderen** — aantal aanpassen met +/− of een product volledig uit de voorraad verwijderen.
- **Scangeschiedenis** — per product zichtbaar hoe vaak en wanneer het gescand is.
- **Boodschappenlijstje** — los af te vinken lijstje, handmatig aan te vullen of vanuit een productdetailscherm.

## Techstack

- Kotlin, Jetpack Compose (Material 3), Navigation Compose
- **Cloud Firestore** als gedeelde database (huishoudens delen dezelfde data, met automatische
  offline-cache) + **Firebase Authentication** (anonieme sign-in — er is geen wachtwoord-login,
  de huishouden-code is de enige toegangscontrole) + **Firebase Storage** (gedeelde profielfoto's
  van huisgenoten in de huishouden-ledenlijst)
- CameraX + ML Kit Barcode Scanning
- Retrofit + Gson richting Open Food Facts
- Coil voor productafbeeldingen
- Handmatige DI (`AppContainer`) — bewust geen Hilt/Koin, de app is klein genoeg dat dat alleen ceremonie toevoegt

Package: `com.dtraas.homestock` · `minSdk 26` · `targetSdk/compileSdk 34`.

## Projectstructuur

```
app/src/main/java/com/dtraas/homestock/
├── data/
│   ├── local/entity/  Firestore-document-modellen (toMap()/fromDocument())
│   ├── local/dao/      Overgebleven platte DTO's (joins/aggregaties), geen Room meer
│   ├── remote/         Open Food Facts API, categorie-mapper, Firestore↔Flow-adapter
│   ├── repository/     Product/Inventory/ShoppingList/ActivityLog/Statistics + Household
│   └── model/          Category/Store/ActivityType enums
├── di/                 AppContainer (handmatige DI)
└── ui/
    ├── household/       Huishouden aanmaken/aansluiten (getoond vóór de rest van de app)
    ├── scan/            Camera preview + barcode-analyzer
    ├── scanresult/       Bevestig/vul aan na scan → toevoegen aan voorraad
    ├── inventory/        Voorraadoverzicht per categorie
    ├── productdetail/    Productdetail + geschiedenis
    ├── shoppinglist/     Boodschappenlijstje
    ├── notifications/    Meldingen + activiteitengeschiedenis
    ├── statistics/       Scanstatistieken
    ├── more/             "Meer"-tab (statistieken, huishouden verlaten)
    ├── navigation/       NavHost + bottom navigation
    ├── components/       Herbruikbare UI (CategoryDropdown, QuantityStepper, ProductImage)
    └── theme/            Material 3 theming
```

## Firebase-project opzetten (eenmalig, vereist)

De app heeft een eigen Firebase-project nodig om te kunnen draaien — dit kan ik niet voor je
aanmaken, dat moet je zelf doen (gratis):

1. Ga naar de [Firebase Console](https://console.firebase.google.com/) en maak een nieuw project aan.
2. Voeg een Android-app toe met pakketnaam `com.dtraas.homestock`.
3. Download het gegenereerde `google-services.json` en plaats het in de map `app/` van dit
   project (naast `build.gradle.kts`). Dit bestand staat in `.gitignore` — commit het niet.
4. Zet in de Firebase Console **Authentication → Sign-in method → Anonymous** aan.
5. Zet **Firestore Database** aan (kies een locatie, "production mode" is prima).
6. Plak de inhoud van `firestore.rules` (in de root van dit project) in **Firestore Database →
   Rules** en publiceer ze. Zonder deze stap weigert Firestore alle lees/schrijf-verzoeken.
7. Zet **Storage** aan (kies een locatie, "production mode" is prima) — dit backt de
   profielfoto's van huisgenoten in de huishouden-ledenlijst (Instellingen > Huishouden).
8. Plak de inhoud van `storage.rules` (in de root van dit project) in **Storage → Rules** en
   publiceer ze. Zonder deze stap weigert Storage alle lees/schrijf-verzoeken (het uploaden
   van een profielfoto mislukt dan stil op de achtergrond — de rest van de app blijft werken).

Zonder deze stappen start de app niet (of kan geen huishouden aanmaken/koppelen).

## Toegangsmodel: wat de huishouden-code beschermt, en wat (nog) niet

De huishouden-code is bewust de enige toegangscontrole (zie `firestore.rules`'s eigen
class-doc) — passend bij een lichte gezins-/huisgenotenapp, niet bedoeld als harde
beveiligingsgrens. Concreet betekent dat: wie de code kent (of raadt — 6 tekens uit een
33-teken alfabet, dus ~1,3 miljard mogelijkheden, zonder rate limiting) heeft volledige
lees/schrijftoegang tot dat huishouden. Prima voor het beoogde gebruik; iets om bewust van te
blijven als de app ooit gevoeligere data zou gaan bevatten.

**App Check is nu aan de clientkant aangesloten** (zie `HomeStockApplication.installAppCheck`)
— elke Firestore/Auth/Functions-aanroep draagt vanaf nu een token mee dat bewijst dat de
aanroep echt van deze eigen, ongewijzigde app komt (Play Integrity in release, een
debug-token in debug builds), in plaats van een gescript verzoek dat de huishouden-code gewoon
raadt of een Cloud Function-aanroep met de hand in elkaar zet. Dit token wordt op dit moment
nog **niet afgedwongen** — Firebase negeert het gewoon totdat je het zelf inschakelt, dus deze
stap alleen verandert nog niets aan wat werkt. Om het echt te laten gelden:

1. Firebase Console → App Check → registreer de Android-app met de Play Integrity-provider
   (en voor debug builds: voeg het debug-token toe dat bij de eerste opstart in Logcat
   verschijnt, via "Manage debug tokens").
2. Laat het een tijdje in de metrics-weergave meelopen (Firebase Console → App Check laat
   geverifieerd-vs-ongeverifieerd verkeer zien) om te controleren dat legitieme app-aanroepen
   ook echt als geverifieerd binnenkomen, vóórdat je afdwinging aanzet.
3. Pas dan: zet afdwinging aan per product (Firestore, en optioneel `enforceAppCheck: true`
   toevoegen aan de `onCall`-opties in `functions/src/index.ts` voor de Cloud Functions).

Net als bij de `verifyPurchase`-stappen hierboven: bewust in losse, controleerbare stappen in
plaats van in één keer afdwingen, want afdwingen vóórdat elk legitiem toestel (inclusief je
eigen testtoestellen) een geldig token krijgt, sluit die toestellen zelf ook buiten.

**Andere opties, niet geïmplementeerd, om te overwegen als dit ooit een groter/publieker
product wordt:**

- **Rate limiting op `joinHousehold`** (client of via een Cloud Function ervoor) — beperkt hoe
  snel iemand codes kan afraden, onafhankelijk van App Check.
  <br>Effort: laag · Impact: verkleint het brute-force-venster aanzienlijk zonder de rest van
  het model te veranderen.
- **Een explicieter lidmaatschapsmodel** — bijv. een eigenaar/beheerder-rol per huishouden die
  nieuwe aansluitverzoeken moet goedkeuren, in plaats van dat de code zelf volledige toegang
  geeft. Dit is een echte productbeslissing (verandert de "iedereen met de code kan meteen
  mee doen"-belofte die de app nu juist laagdrempelig maakt) — bewust niet zomaar
  doorgevoerd zonder dat expliciet te bespreken.
  <br>Effort: hoog (raakt join-flow, meerdere schermen, Cloud Functions) · Impact: sluit het
  belangrijkste resterende gat echt af, ten koste van laagdrempeligheid.
- **Firestore-brede audit-logging** (Cloud Audit Logs voor Firestore, in Google Cloud Console)
  — geeft achteraf inzicht bij een vermoeden van misbruik, voorkomt niets zelf.
  <br>Effort: laag (aanvinken in Cloud Console) · Impact: forensisch, niet preventief.

## Play Console opzetten (eenmalig, vereist voor Premium/in-app aankopen)

De app zelf kan geen Play Console-producten aanmaken (zie de doc-comments in
`BillingRepository.kt`) — dat moet je zelf doen, in de Play Console van je eigen
ontwikkelaarsaccount, met **exact** deze product-id's:

| Product-id | Type | Notities |
| --- | --- | --- |
| `premium_monthly` | Abonnement | Basisplan met een gratis-proefperiode-aanbod (zie hieronder). |
| `premium_yearly` | Abonnement | Idem, met een aanbod dat goedkoper uitpakt dan 12× de maandprijs — dat verschil wordt automatisch als "Bespaar X%" getoond, dus geen aparte configuratie in de app nodig. |

Dit zijn de twee enige koopbare producten — beide ontgrendelen exact dezelfde ene Premium-laag
(inclusief een onbeperkt aantal huishoudleden), alleen het facturatie-ritme verschilt. Er was
hiernaast ooit een losse eenmalige "Levenslang"-aankoop en een apart huishouden-uitbreidingspakket
(`premium_lifetime`/`premium_unlimited_members`); beide zijn samengevoegd tot deze ene laag om de
aankoopbeslissing simpel te houden. Bestaande Play Console-producten met die id's mogen blijven
staan (iemand die ze al kocht, verliest niets), de app biedt en bevraagt ze alleen niet meer.

Voor beide abonnementen: maak een aanbod ("offer") met twee prijsfases — een gratis fase
(free trial) gevolgd door de doorlopende prijs. De lengte van die proefperiode staat nergens
in de code zelf; hij wordt alleen getoond via de `trial_days`-waarde in Remote Config
hieronder, dus hou die twee handmatig gelijk als je de proefperiode ooit wijzigt.

**Remote Config** (Firebase Console → Remote Config — zelfde project als hierboven): dit werkt
ook zonder dat je hier iets instelt (de app valt terug op ingebouwde standaardwaarden), maar om
ze op afstand te kunnen bijstellen zonder appupdate, voeg je deze parameters toe:

| Parameter | Type | Standaard |
| --- | --- | --- |
| `trial_days` | Number | `7` — alleen voor de tekst in de app; moet gelijk blijven aan de proefperiode die je in de Play Console-aanbieding hierboven instelt. |
| `monthly_plan_enabled` | Boolean | `true` — noodrem om de maandelijkse kaart te verbergen zonder appupdate. |

**Analytics**: werkt automatisch zodra Google Analytics gekoppeld is aan het Firebase-project
(vink je meestal aan bij het aanmaken van het project hierboven; kan achteraf ook via Firebase
Console → Projectinstellingen → Integraties). Geen paywall-events zonder die koppeling.

Zonder de Play Console-producten hierboven blijft de Premium-betaalmuur werken, maar toont
iedere prijs "Prijs laden…" en is geen enkele koop-knop klikbaar.

### Server-side verificatie van Premium (`verifyPurchase`)

`isPremium` wordt van huis uit door het toestel zelf afgeleid uit wat de Play Billing Library
teruggeeft — genoeg om de UI te sturen, maar een aangepaste APK zou dat in theorie kunnen
vervalsen. De `verifyPurchase` Cloud Function (zie `functions/src/index.ts`) controleert een
aankoop in plaats daarvan rechtstreeks bij Google (de Play Developer API) en schrijft het
geverifieerde resultaat zelf naar Firestore. De app roept deze functie al automatisch aan bij
elke actieve aankoop (`HouseholdMembersRepository.verifyPurchases`) — er is maar één handmatige
stap nodig om hem ook daadwerkelijk te laten werken:

1. **Play Console → Gebruikers en machtigingen → Nieuwe gebruikers uitnodigen.** Nodig het
   service-account van je Cloud Functions-project uit (standaard
   `<project-id>@appspot.gserviceaccount.com`, te vinden via Firebase Console →
   Projectinstellingen → Serviceaccounts) met minimaal de machtiging **"Financiële gegevens,
   orders en enquêtereacties over annuleringen bekijken"**.
2. Deploy `functions` opnieuw zodat `verifyPurchase` live staat.

Zonder deze stap blijft alles gewoon werken zoals nu — `verifyPurchases` faalt dan stil op elke
aanroep (zie de doc-comment erboven) en `isPremium` blijft net als voorheen volledig
toestel-afgeleid.

**Belangrijk — dit is bewust nog geen harde afdwinging.** Zolang `firestore.rules` een
signed-in client toestaat om `isPremium` op zijn eigen member-document te schrijven (de huidige
regel), kan een aangepaste APK dat nog steeds direct doen, los van `verifyPurchase`. Die
server-verificatie wordt pas de *echte* beveiligingsgrens zodra je, ná het bevestigen dat stap 1
hierboven werkt (controleer een testaankoop en kijk of `isPremiumVerifiedAt` op het
member-document verschijnt), zelf deze twee dingen doet:

1. In `firestore.rules`, onder `match /households/{householdId}`, een specifiekere
   `match /members/{uid}` regel toevoegen die een client-`create`/`update` geen `isPremium`- of
   `isPremiumVerifiedAt`-veld meer laat aanraken (`request.resource.data.diff(resource.data)
   .affectedKeys().hasAny([...])`), zodat alleen de Admin SDK (dus `verifyPurchase`) dat veld
   nog kan zetten.
2. In `HouseholdMembersRepository.kt`, de `syncPremiumStatus`-aanroep in `init` verwijderen
   (die client-write zou na stap 1 toch alleen nog een permission-denied opleveren).

Dat bewust in twee losse stappen: de eerste is zonder risico (verandert niets aan wat al werkt),
de tweede zet de daadwerkelijke beveiligingsgrens en moet je pas doen als je zeker weet dat
`verifyPurchase` betrouwbaar aanslaat — anders verliest iedereen die net een abonnement heeft
afgesloten in de tussentijd zijn Premium.

## Bouwen

Open het project in Android Studio (Ladybug of nieuwer) en laat Gradle syncen, of vanaf de command line:

```
./gradlew assembleDebug
```

> **Let op:** dit project is geschreven in een sandbox-omgeving zonder Android SDK en zonder
> toegang tot `dl.google.com`/`maven.google.com` (het Android/Google Maven-repository), dus de
> build kon hier niet daadwerkelijk gedraaid of in een emulator getest worden. De code is met
> zorg geschreven volgens standaardpatronen, maar controleer bij de eerste build in Android
> Studio vooral even:
> - of de dependency-versies in `gradle/libs.versions.toml` nog actueel zijn (Android Studio
>   stelt automatisch updates voor via "Upgrade" hints als een versie niet meer bestaat) —
>   dit geldt zeker voor de Firebase BoM-versie,
> - of de Gradle sync zonder fouten doorloopt (google-services-plugin, Compose-compilerplugin),
> - of `app/google-services.json` aanwezig is (zie hierboven) — zonder dat bestand faalt de
>   build met een duidelijke foutmelding van de google-services-plugin.

## Ontbrekende/toekomstige uitbreidingen

- Camera-permissie-afwijzing leidt nu naar een simpele uitleg-scherm; een deep link naar de
  systeeminstellingen kan gebruiksvriendelijker.
- Er is geen "lage voorraad"-signalering; dat zou een logische vervolgstap zijn om het
  boodschappenlijstje automatisch aan te vullen.
- Geen unit tests.
- Firestore-collecties (scangeschiedenis, activiteitenlog) groeien ongelimiteerd; voor een
  huishouden dat jarenlang actief blijft is periodiek opschonen een logische vervolgstap.
