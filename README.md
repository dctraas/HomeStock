# Boodschp

Android-app om bij te houden welke boodschappen je nog in huis hebt.

## Functionaliteit

- **Barcode scannen** — CameraX + ML Kit Barcode Scanning, volledig on-device (geen API-key nodig).
- **Productgegevens ophalen** — via de gratis, sleutelloze [Open Food Facts](https://world.openfoodfacts.org/) API; resultaten worden lokaal gecachet in Room. Onbekende barcodes kun je handmatig invullen.
- **Voorraadoverzicht per categorie** — gegroepeerd met sticky headers (Zuivel, Groente & Fruit, Vlees & Vis, Dranken, …), categorie wordt automatisch geraden op basis van de Open Food Facts data.
- **Toevoegen/verwijderen** — aantal aanpassen met +/− of een product volledig uit de voorraad verwijderen.
- **Scangeschiedenis** — per product zichtbaar hoe vaak en wanneer het gescand is.
- **Boodschappenlijstje** — los af te vinken lijstje, handmatig aan te vullen of vanuit een productdetailscherm.

## Techstack

- Kotlin, Jetpack Compose (Material 3), Navigation Compose
- Room (lokale database, met een transactionele scan-flow voor voorraad + geschiedenis)
- CameraX + ML Kit Barcode Scanning
- Retrofit + Gson richting Open Food Facts
- Coil voor productafbeeldingen
- Handmatige DI (`AppContainer`) — bewust geen Hilt/Koin, de app is klein genoeg dat dat alleen ceremonie toevoegt

Package: `com.dtraas.boodschp` · `minSdk 26` · `targetSdk/compileSdk 34`.

## Projectstructuur

```
app/src/main/java/com/dtraas/boodschp/
├── data/
│   ├── local/        Room entities, DAOs, AppDatabase
│   ├── remote/        Open Food Facts API + categorie-mapper
│   ├── repository/    ProductRepository, InventoryRepository, ShoppingListRepository
│   └── model/         Category enum
├── di/                 AppContainer (handmatige DI)
└── ui/
    ├── scan/           Camera preview + barcode-analyzer
    ├── scanresult/      Bevestig/vul aan na scan → toevoegen aan voorraad
    ├── inventory/       Voorraadoverzicht per categorie
    ├── productdetail/   Productdetail + geschiedenis
    ├── shoppinglist/    Boodschappenlijstje
    ├── navigation/      NavHost + bottom navigation
    ├── components/      Herbruikbare UI (CategoryDropdown, QuantityStepper)
    └── theme/           Material 3 theming
```

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
>   stelt automatisch updates voor via "Upgrade" hints als een versie niet meer bestaat),
> - of de Gradle sync zonder fouten doorloopt (KSP/Room-codegen, Compose-compilerplugin).

## Ontbrekende/toekomstige uitbreidingen

- Camera-permissie-afwijzing leidt nu naar een simpele uitleg-scherm; een deep link naar de
  systeeminstellingen kan gebruiksvriendelijker.
- Er is geen "lage voorraad"-signalering; dat zou een logische vervolgstap zijn om het
  boodschappenlijstje automatisch aan te vullen.
- Geen unit tests; de repository-laag leent zich goed voor Room in-memory database tests.
