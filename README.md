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

## Play Console opzetten (eenmalig, vereist voor Premium/in-app aankopen)

De app zelf kan geen Play Console-producten aanmaken (zie de doc-comments in
`BillingRepository.kt`) — dat moet je zelf doen, in de Play Console van je eigen
ontwikkelaarsaccount, met **exact** deze product-id's:

| Product-id | Type | Notities |
| --- | --- | --- |
| `premium_monthly` | Abonnement | Basisplan met een gratis-proefperiode-aanbod (zie hieronder). |
| `premium_yearly` | Abonnement | Idem, met een aanbod dat goedkoper uitpakt dan 12× de maandprijs — dat verschil wordt automatisch als "Bespaar X%" getoond, dus geen aparte configuratie in de app nodig. |
| `premium_lifetime` | Eenmalig (managed) | Voor gebruikers die liever één keer betalen dan een abonnement nemen. Nooit als consumable instellen — de app consumeert 'm nooit, alleen "acknowledge". |
| `premium_unlimited_members` | Eenmalig (managed) | Het huishouden-uitbreidingspakket (zie `HouseholdMembersRepository`) — heft de Premium-ledenlimiet voor dat hele huishouden op. Ook nooit als consumable instellen. |

Voor beide abonnementen: maak een aanbod ("offer") met twee prijsfases — een gratis fase
(free trial) gevolgd door de doorlopende prijs. De lengte van die proefperiode staat nergens
in de code zelf; hij wordt alleen getoond via de `trial_days`-waarde in Remote Config
hieronder, dus hou die twee handmatig gelijk als je de proefperiode ooit wijzigt.

**Remote Config** (Firebase Console → Remote Config — zelfde project als hierboven): dit werkt
ook zonder dat je hier iets instelt (de app valt terug op ingebouwde standaardwaarden), maar om
ze op afstand te kunnen bijstellen zonder appupdate, voeg je deze parameters toe:

| Parameter | Type | Standaard |
| --- | --- | --- |
| `premium_member_cap` | Number | `10` — max. huishoudleden met Premium, zonder het `premium_unlimited_members`-pakket. |
| `trial_days` | Number | `7` — alleen voor de tekst in de app; moet gelijk blijven aan de proefperiode die je in de Play Console-aanbieding hierboven instelt. |
| `monthly_plan_enabled` | Boolean | `true` — noodrem om de maandelijkse kaart te verbergen zonder appupdate. |

**Analytics**: werkt automatisch zodra Google Analytics gekoppeld is aan het Firebase-project
(vink je meestal aan bij het aanmaken van het project hierboven; kan achteraf ook via Firebase
Console → Projectinstellingen → Integraties). Geen paywall-events zonder die koppeling.

Zonder de Play Console-producten hierboven blijft de Premium-betaalmuur werken, maar toont
iedere prijs "Prijs laden…" en is geen enkele koop-knop klikbaar.

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
