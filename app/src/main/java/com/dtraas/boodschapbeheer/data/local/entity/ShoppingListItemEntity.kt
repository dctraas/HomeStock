package com.dtraas.boodschapbeheer.data.local.entity

import com.dtraas.boodschapbeheer.data.model.MeasurementUnit
import com.google.firebase.firestore.DocumentSnapshot

/**
 * A single line on the shopping list. [barcode] is nullable because items
 * can be typed in by hand as well as added from a scanned/known product.
 * Stored at `households/{householdId}/shoppingList/{id}`; [id] is the
 * Firestore-generated document id.
 *
 * [store] is the store's plain display name (e.g. "Albert Heijn"), not a reference to a
 * [com.dtraas.boodschapbeheer.data.repository.StoreRepository] document — an empty string
 * means "no store". That keeps grouping/display a pure string operation with no store list
 * to join against, and means deleting a custom store never orphans an item that used it.
 */
data class ShoppingListItemEntity(
    val id: String,
    val barcode: String?,
    val name: String,
    val category: String,
    val store: String,
    val imageUrl: String?,
    val quantity: Int,
    val isChecked: Boolean,
    val addedAt: Long,
    val note: String? = null,
    // Ascending sort key for manual (drag-to-reorder) ordering. Defaults to -addedAt so
    // freshly added items sort first without a migration or extra read; a drag just sets
    // this to the midpoint between its new neighbors' values, so reordering is an O(1)
    // single-document write regardless of how far an item moves.
    val sortOrder: Double = -addedAt.toDouble(),
    // Storage key of the MeasurementUnit quantity is expressed in (stuks/gram/kg/ml/L).
    val unit: String = MeasurementUnit.STUKS.storageKey,
    // What was paid for this item, entered by hand — there's no price API to fetch it from.
    val price: Double? = null,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "barcode" to barcode,
        "name" to name,
        "category" to category,
        "store" to store,
        "imageUrl" to imageUrl,
        "quantity" to quantity,
        "isChecked" to isChecked,
        "addedAt" to addedAt,
        "note" to note,
        "sortOrder" to sortOrder,
        "unit" to unit,
        "price" to price,
    )

    companion object {
        // The store field used to hold one of the old fixed Store enum's storage keys
        // (ah/jumbo/.../geen) instead of a plain name — resolved here, once, at the read
        // boundary, so nothing downstream ever has to know this history existed.
        private val legacyStoreKeyNames = mapOf(
            "ah" to "Albert Heijn",
            "jumbo" to "Jumbo",
            "nettorama" to "Nettorama",
            "kruidvat" to "Kruidvat",
            "hema" to "Hema",
            "etos" to "Etos",
            "action" to "Action",
            "geen" to "",
        )

        fun fromDocument(document: DocumentSnapshot): ShoppingListItemEntity? {
            val name = document.getString("name") ?: return null
            val addedAt = document.getLong("addedAt") ?: 0L
            val rawStore = document.getString("store") ?: ""
            return ShoppingListItemEntity(
                id = document.id,
                barcode = document.getString("barcode"),
                name = name,
                category = document.getString("category") ?: "overig",
                store = legacyStoreKeyNames[rawStore] ?: rawStore,
                imageUrl = document.getString("imageUrl"),
                quantity = (document.getLong("quantity") ?: 1L).toInt(),
                isChecked = document.getBoolean("isChecked") ?: false,
                addedAt = addedAt,
                note = document.getString("note")?.takeIf { it.isNotBlank() },
                sortOrder = document.getDouble("sortOrder") ?: -addedAt.toDouble(),
                unit = document.getString("unit") ?: MeasurementUnit.STUKS.storageKey,
                price = (document.get("price") as? Number)?.toDouble(),
            )
        }
    }
}
