package com.example.roleaf.model

data class IdentificationResult(
    val plantNetTop: PlantNetResult?,
    val treflePlant: TreflePlant?,
    val wikiExtract: String?,      // lead/summary
    val wikiSections: String?,     // full non-lead sections (preferred for General Guide)
    val careGuide: CareGuide?,
    val similarSpecies: List<SimilarSpeciesItem>,
    val morphology: MorphologyProfile?,
    val nativeRange: NativeRange?
)

data class CareGuide(
    val watering: String? = null,
    val light: String? = null,
    val soil: String? = null,
    val temperature: String? = null,
    val notes: String? = null
)

data class SimilarSpeciesItem(
    val scientificName: String,
    val score: Double? = null,
    val imageUrl: String? = null
)

data class MorphologyProfile(
    val habit: String? = null,
    val leaves: String? = null,
    val flowers: String? = null,
    val fruit: String? = null
)

data class NativeRange(
    val nativeList: List<String> = emptyList(),
    val summary: String? = null
)
