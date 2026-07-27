package com.dtraas.boodschp.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Response shape of the Open Food Facts "get product by barcode" endpoint. */
data class OffProductResponse(
    @SerializedName("status") val status: Int,
    @SerializedName("product") val product: OffProduct?,
)

data class OffProduct(
    @SerializedName("product_name") val productName: String?,
    @SerializedName("brands") val brands: String?,
    @SerializedName("categories_tags") val categoriesTags: List<String>?,
    @SerializedName("categories") val categories: String?,
    @SerializedName("image_front_small_url") val imageUrl: String?,
    @SerializedName("quantity") val quantity: String?,
)
