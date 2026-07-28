package com.dtraas.boodschapbeheer.data.model

/** A short "what's new" style message from the app's developer, shown on the Nieuws screen. */
data class DeveloperNotice(
    val title: String,
    val message: String,
)

object DeveloperNotices {
    /** Newest first. Static for now — there's no backend to fetch these from. */
    val all: List<DeveloperNotice> = listOf(
        DeveloperNotice(
            title = "Delen met huisgenoten",
            message = "Maak een huishouden aan of sluit aan met een code (via Meer) om je voorraad " +
                "en boodschappenlijst met huisgenoten te delen.",
        ),
        DeveloperNotice(
            title = "Meldingen",
            message = "Alle app- en ontwikkelaarsmeldingen staan voortaan hier bij elkaar.",
        ),
        DeveloperNotice(
            title = "Nieuw kleurenpalet",
            message = "De app heeft een frisser kleurenpalet gekregen: groen, terracotta en bosbessenblauw.",
        ),
        DeveloperNotice(
            title = "Sorteeropties in Voorraad",
            message = "Sorteer je voorraad op naam, aantal of laatst toegevoegd.",
        ),
        DeveloperNotice(
            title = "Sneller scannen",
            message = "Bekende producten worden nu direct toegevoegd tijdens het scannen, zonder tussenstap.",
        ),
        DeveloperNotice(
            title = "Ongedaan maken",
            message = "Per ongeluk iets verwijderd? Zet het direct terug via de melding onderin het scherm.",
        ),
        DeveloperNotice(
            title = "Voorraad als kaarten",
            message = "Schakel in Voorraad tussen lijst- en kaartweergave (2 per rij).",
        ),
        DeveloperNotice(
            title = "Boodschappenlijst per winkel",
            message = "Je boodschappenlijst is ingedeeld per winkel, met bewerken en productfoto's.",
        ),
        DeveloperNotice(
            title = "Statistieken toegevoegd",
            message = "Bekijk je meest gescande producten en de verdeling per categorie.",
        ),
    )
}
