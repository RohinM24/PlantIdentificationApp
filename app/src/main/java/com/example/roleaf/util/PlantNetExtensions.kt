package com.example.roleaf.util

import com.example.roleaf.model.*
import com.example.roleaf.model.TreflePlant

fun PlantNetResult.imageUrls(): List<String> {
    val urlsFromStrings = this.images ?: emptyList()
    val urlsFromObjects = this.image_objects?.mapNotNull { it.image_url } ?: emptyList()
    return (urlsFromStrings + urlsFromObjects).filter { !it.isNullOrBlank() }.distinct()
}

fun PlantNetResult.primaryCommonName(): String? =
    this.species?.commonNames?.firstOrNull()

fun PlantNetResult.scientificName(): String? =
    this.species?.scientificNameWithoutAuthor ?: this.species?.scientificName

fun PlantNetResult.detectedOrgan(): String? =
    this.image_objects?.firstOrNull { !it.organ.isNullOrBlank() }?.organ ?: this.image_objects?.firstOrNull()?.organ

fun PlantNetResult.similarSpeciesList(): List<SimilarSpeciesItem> {
    val list = mutableListOf<SimilarSpeciesItem>()
    // Prefer synonyms (safe), else fall back to commonNames or genus
    this.synonyms?.forEach { s ->
        if (!s.isNullOrBlank()) list.add(SimilarSpeciesItem(scientificName = s, imageUrl = null))
    }
    if (list.isEmpty()) {
        val common = this.species?.commonNames ?: emptyList()
        common.take(6).forEach { c ->
            if (!c.isNullOrBlank()) list.add(SimilarSpeciesItem(scientificName = c, imageUrl = null))
        }
    }
    // as a last fallback, include genus name
    this.taxonomy?.get("genus")?.let { g ->
        if (list.size < 3 && !g.isNullOrBlank()) list.add(SimilarSpeciesItem(scientificName = g, imageUrl = null))
    }
    return list
}

fun TreflePlant.nativeRangeFallback(): com.example.roleaf.model.NativeRange? {
    try {
        val list1 = this.distributions?.native?.mapNotNull { it.name } ?: emptyList()
        val list2 = this.distribution?.native ?: emptyList()
        val native = (list1 + list2).distinct()
        if (native.isNotEmpty()) {
            return com.example.roleaf.model.NativeRange(nativeList = native, summary = native.joinToString(", "))
        }
    } catch (_: Exception) { }
    return null
}






