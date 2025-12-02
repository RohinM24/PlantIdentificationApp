package com.example.roleaf.model

/**
 * Extended PlantNet response model with organs, synonyms and taxonomy.
 * Matches the PlantNet /identify v2 structure in a minimal, safe way.
 */

data class PlantNetResponse(
    val results: List<PlantNetResult>?
)

data class PlantNetResult(
    val score: Double? = null,
    val species: PNSpecies? = null,
    val images: List<String>? = null, // older UI expects List<String>
    // plantnet sometimes returns richer image objects; we include them too
    val image_objects: List<PNImage>? = null,
    val synonyms: List<String>? = null,
    val taxonomy: Map<String, String>? = null
) {
    // Backward compat helper: return a simple image url list for UI
    fun imageUrls(): List<String> {
        val urlsFromStrings = images ?: emptyList()
        val urlsFromObjects = image_objects?.mapNotNull { it.image_url } ?: emptyList()
        return (urlsFromStrings + urlsFromObjects).distinct()
    }

    // convenience to get best percent (0-100)
    fun bestScorePercent(): Int = ((score ?: 0.0) * 100).toInt()

    fun primaryCommonName(): String? = species?.commonNames?.firstOrNull()

    fun scientificName(): String? = species?.scientificNameWithoutAuthor ?: species?.scientificName
}

data class PNSpecies(
    val scientificNameWithoutAuthor: String? = null,
    val scientificNameAuthorship: String? = null,
    val scientificName: String? = null,
    val genus: PNGenus? = null,
    val family: PNFamily? = null,
    val commonNames: List<String> = emptyList()
)

data class PNGenus(
    val scientificNameWithoutAuthor: String? = null,
    val scientificName: String? = null
)

data class PNFamily(
    val scientificNameWithoutAuthor: String? = null,
    val scientificName: String? = null
)

data class PNImage(
    val id: Long? = null,
    val image_url: String? = null,
    val filename: String? = null,
    val organ: String? = null // e.g. "leaf", "flower"
)



