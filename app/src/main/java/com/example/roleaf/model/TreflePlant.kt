// app/src/main/java/com/example/roleaf/model/TreflePlant.kt
package com.example.roleaf.model

data class TrefleResponse(val data: TreflePlant?)
data class TrefleListResponse(val data: List<TreflePlant>?) // for search endpoints that return list

data class TreflePlant(
    val id: Int? = null,
    val common_name: String? = null,
    val scientific_name: String? = null,
    val image_url: String? = null,
    val images: List<TrefleImage>? = emptyList(),
    val growth: Growth? = null,
    val distributions: DistributionsWrapper? = null,
    val distribution: DistributionWrapper? = null,
    val genus: TrefleGenus? = null,
    val family: String? = null
)

data class TrefleImage(
    val id: Long? = null,
    // common canonical field
    val url: String? = null,

    // alternate names sometimes returned by Trefle / other image wrappers
    val image_url: String? = null,
    val original_url: String? = null,
    val src: String? = null,
    val source: String? = null,
    val thumbnail_url: String? = null,
    val large_url: String? = null,
    val filename: String? = null,
    val caption: String? = null,
    val type: String? = null
)

data class Growth(
    val description: String? = null,
    val light: String? = null, // sometimes numeric in API; we store as String for friendly text
    val soil_texture: String? = null,
    val soil_nutriments: String? = null,
    val soil_ph_minimum: Float? = null,
    val soil_ph_maximum: Float? = null,
    val minimum_temperature: Temperature? = null,
    val maximum_temperature: Temperature? = null
)

data class Temperature(
    val deg_c: Float? = null,
    val deg_f: Float? = null
)

// Trefle sometimes returns distributions in one field or another; include wrappers
data class DistributionsWrapper(
    val native: List<DistributionItem>? = emptyList(),
    val introduced: List<DistributionItem>? = emptyList()
)

data class DistributionWrapper(
    val native: List<String>? = emptyList()
)

data class DistributionItem(
    val id: Int? = null,
    val name: String? = null,
    val slug: String? = null
)

data class TrefleGenus(
    val id: Int? = null,
    val name: String? = null,
    val slug: String? = null
)




