package com.dtraas.boodschapbeheer.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Response shape of Open Food Facts' free-text product search endpoint. */
data class OffSearchResponse(
    @SerializedName("products") val products: List<OffSearchProduct>?,
)

data class OffSearchProduct(
    @SerializedName("code") val code: String?,
    @SerializedName("product_name") val productName: String?,
    @SerializedName("brands") val brands: String?,
    @SerializedName("image_front_small_url") val imageUrl: String?,
)
