// app/src/main/java/com/example/roleaf/data/IdentificationRepository.kt
package com.example.roleaf.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.roleaf.api.PlantNetApi
import com.example.roleaf.api.TrefleApi
import com.example.roleaf.api.WikipediaApi
import com.example.roleaf.model.*
import com.example.roleaf.util.nativeRangeFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.net.URLEncoder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.Gson
import com.example.roleaf.data.WikiFormatter
import com.example.roleaf.util.similarSpeciesList
import okhttp3.ResponseBody

class IdentificationRepository(
    private val context: Context,
    private val plantNetService: PlantNetApi,
    private val trefleService: TrefleApi,
    private val wikiService: WikipediaApi,
    private val plantNetKey: String,
    private val trefleKey: String
) {

    private val TAG = "RoLeaf"
    private val S_TAG = "SIMILAR"

    // caps (tweak if you want)
    private val EXTRACT_MAX = 2000
    private val SECTIONS_MAX = 8000

    suspend fun identify(uriList: List<Uri>, organsList: List<String>): Triple<PlantNetResult?, TreflePlant?, Pair<String?, String?>?> =
        withContext(Dispatchers.IO) {
            // ensure organsList length matches uriList length: PlantNet expects images[i] <-> organs[i]
            val organs: List<String> = if (organsList.size == uriList.size) {
                organsList
            } else {
                Log.w(TAG, "identify: mismatch: uriList.size=${uriList.size} organsList.size=${organsList.size}. Adjusting organs list to match images.")
                // If there are fewer organs provided, pad with "other"; if more, truncate.
                val adjusted = mutableListOf<String>()
                for (i in 0 until uriList.size) {
                    adjusted.add(organsList.getOrNull(i) ?: "other")
                }
                adjusted
            }

            val imageParts = prepareImageParts(uriList)

// Send organs as a JSON array (e.g. ["leaf","flower"]) — PlantNet expects a non-plain-text type
            val organsJson = try {
                Gson().toJson(organsList)
            } catch (e: Exception) {
                // fallback to best-effort CSV if gson fails (very unlikely)
                organsList.joinToString(",")
            }
            val organsPart: RequestBody = RequestBody.create(
                "application/json; charset=utf-8".toMediaTypeOrNull(),
                organsJson
            )

            val plantResp = try {
                plantNetService.identifyPlant(imageParts, organsPart, plantNetKey)
            } catch (e: Exception) {
                Log.e(TAG, "❌ PlantNet request failed: ${e.message}", e)
                return@withContext Triple(null, null, null)
            }


            if (!plantResp.isSuccessful) {
                Log.w(TAG, "PlantNet not successful: code=${plantResp.code()}")
                return@withContext Triple(null, null, null)
            }

            val results = plantResp.body()?.results
            val top = results?.maxByOrNull { it.score ?: 0.0 }
            if (top == null) {
                Log.w(TAG, "No PlantNet top result")
                return@withContext Triple(null, null, null)
            }

            val sci = top.species?.scientificNameWithoutAuthor ?: top.species?.scientificName ?: "Unknown"

            val trefle = fetchTrefleByName(sci, top.synonyms ?: top.species?.commonNames)
            // fetch summary (lead) + larger non-lead sections (wikiPair.second)
            val wikiPair = fetchWikipediaWithLanguageFallback(sci, top.synonyms ?: top.species?.commonNames)

            // enforce caps here before returning to UI
            val cappedExtract = wikiPair?.first?.let { if (it.length > EXTRACT_MAX) it.take(EXTRACT_MAX).trimEnd() + "…" else it }
            val cappedSections = wikiPair?.second?.let { if (it.length > SECTIONS_MAX) it.take(SECTIONS_MAX).trimEnd() + "…" else it }

            Triple(top, trefle, Pair(cappedExtract, cappedSections))
        }

    suspend fun identifyWithExtras(uriList: List<Uri>, organsList: List<String>): IdentificationResult? =
        withContext(Dispatchers.IO) {
            val (plantTop, trefle, wikiPair) = identify(uriList, organsList)
            val wikiExtract = wikiPair?.first
            val wikiSections = wikiPair?.second

            if (plantTop == null && trefle == null && wikiExtract.isNullOrBlank() && wikiSections.isNullOrBlank()) {
                Log.d(TAG, "identifyWithExtras: nothing returned from upstream")
                return@withContext null
            }

            // Build CareGuide from Trefle growth if possible
            val careFromTrefle: CareGuide? = trefle?.let { t ->
                try {
                    val growth = t.growth
                    if (growth == null) return@let null

                    val watering = when {
                        !growth.description.isNullOrBlank() -> growth.description
                        !growth.soil_texture.isNullOrBlank() -> "Prefers ${growth.soil_texture}"
                        else -> null
                    }

                    val light = growth.light?.takeIf { it.isNotBlank() }

                    val soilParts = listOfNotNull(
                        growth.soil_texture?.takeIf { it.isNotBlank() },
                        growth.soil_nutriments?.takeIf { it.isNotBlank() }
                    )
                    val soilJoined = soilParts.joinToString(", ")
                    val soil = if (soilJoined.isNotBlank()) soilJoined else null

                    val phMin = growth.soil_ph_minimum?.toString()
                    val phMax = growth.soil_ph_maximum?.toString()
                    val ph = when {
                        !phMin.isNullOrBlank() && !phMax.isNullOrBlank() -> "$phMin — $phMax"
                        !phMin.isNullOrBlank() -> phMin
                        !phMax.isNullOrBlank() -> phMax
                        else -> null
                    }

                    val tempRange = if (growth.minimum_temperature?.deg_c != null || growth.maximum_temperature?.deg_c != null) {
                        val min = growth.minimum_temperature?.deg_c?.toInt()
                        val max = growth.maximum_temperature?.deg_c?.toInt()
                        val parts = listOfNotNull(min?.toString(), max?.toString())
                        if (parts.isNotEmpty()) parts.joinToString("–") + " °C" else null
                    } else null

                    val notes = growth.description?.takeIf { it.isNotBlank() }

                    CareGuide(
                        watering = watering,
                        light = light,
                        soil = if (!soil.isNullOrBlank()) soil else ph,
                        temperature = tempRange,
                        notes = notes
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "care-from-trefle build failed: ${e.message}")
                    null
                }
            }

            // If careFromTrefle is empty or lacks notes, fill notes with wikiSections (preferred) or wikiExtract
            val careWithNotes: CareGuide? = when {
                careFromTrefle != null -> {
                    val hasContent = listOf(careFromTrefle.watering, careFromTrefle.light, careFromTrefle.soil, careFromTrefle.temperature, careFromTrefle.notes).any { !it.isNullOrBlank() }
                    if (!hasContent) {
                        val fill = wikiSections?.takeIf { it.isNotBlank() } ?: wikiExtract?.takeIf { it.isNotBlank() }
                        if (fill != null) careFromTrefle.copy(notes = fill) else careFromTrefle
                    } else {
                        if (careFromTrefle.notes.isNullOrBlank()) {
                            val fill = wikiSections?.takeIf { it.isNotBlank() } ?: wikiExtract?.takeIf { it.isNotBlank() }
                            if (fill != null) careFromTrefle.copy(notes = fill) else careFromTrefle
                        } else {
                            careFromTrefle
                        }
                    }
                }
                // no careFromTrefle: create one with notes=wikiSections/wikiExtract if available
                !wikiSections.isNullOrBlank() -> CareGuide(notes = wikiSections)
                !wikiExtract.isNullOrBlank() -> CareGuide(notes = wikiExtract)
                else -> null
            }

            // Morphology inference
            val morphology = when {
                trefle?.growth?.description?.isNotBlank() == true -> MorphologyProfile(
                    habit = trefle.genus?.name,
                    leaves = null,
                    flowers = null,
                    fruit = null
                )
                else -> {
                    val habitHint = plantTop?.species?.genus?.scientificName ?: plantTop?.species?.commonNames?.firstOrNull()
                    MorphologyProfile(habit = habitHint)
                }
            }

            // ---------- fetch images for similar species (with logs) ----------
            val similar = mutableListOf<SimilarSpeciesItem>()

            // helper: sanitize/normalize image URLs
            fun sanitizeImageUrl(raw: String?): String? {
                if (raw.isNullOrBlank()) return null
                var url = raw.trim()

                // strip surrounding quotes
                if ((url.startsWith("\"") && url.endsWith("\"")) || (url.startsWith("'") && url.endsWith("'"))) {
                    url = url.substring(1, url.length - 1)
                }

                // handle protocol-relative URLs
                if (url.startsWith("//")) {
                    url = "https:$url"
                }

                // prefer https
                if (url.startsWith("http://")) {
                    url = url.replaceFirst("http://", "https://")
                }

                // if it looks like a bare domain/path, add https
                if (!url.startsWith("http://") && !url.startsWith("https://") && url.matches(Regex("^[\\w\\.-]+(/.*)?$"))) {
                    url = "https://$url"
                }

                // Trim whitespace again
                url = url.trim()

                // If it doesn't have an image extension but has obvious image host markers, still return it.
                val lower = url.lowercase()
                if (lower.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp)(\\?.*)?$"))) {
                    return url
                }

                // common CDN/upload markers
                if (lower.contains("upload") || lower.contains("cdn") || lower.contains("images") || lower.contains("imgur") || lower.contains("wikimedia")) {
                    return url
                }

                // if the url contains an http and a slash, return it (best-effort)
                if (url.contains("://") && url.contains("/")) {
                    return url
                }

                // otherwise drop it (likely not an image) — safer than returning garbage
                return null
            }

            // Recursive JSON search for the first image-looking string found.
            fun findFirstImageUrlInJson(el: JsonElement?): String? {
                if (el == null || el.isJsonNull) return null
                try {
                    when {
                        el.isJsonObject -> {
                            val obj = el.asJsonObject

                            // check common keys first
                            val candidateKeys = listOf("image_url", "image", "url", "src", "original_url", "thumbnail_url", "large_url")
                            for (k in candidateKeys) {
                                if (obj.has(k) && obj.get(k).isJsonPrimitive) {
                                    val v = obj.get(k).asString
                                    val s = sanitizeImageUrl(v)
                                    if (!s.isNullOrBlank()) return s
                                }
                            }

                            // otherwise traverse children
                            for ((_, value) in obj.entrySet()) {
                                val found = findFirstImageUrlInJson(value)
                                if (!found.isNullOrBlank()) return found
                            }
                        }
                        el.isJsonArray -> {
                            val arr = el.asJsonArray
                            for (i in 0 until arr.size()) {
                                val found = findFirstImageUrlInJson(arr[i])
                                if (!found.isNullOrBlank()) return found
                            }
                        }
                        el.isJsonPrimitive -> {
                            val prim = el.asJsonPrimitive
                            if (prim.isString) {
                                val s = prim.asString
                                val san = sanitizeImageUrl(s)
                                if (!san.isNullOrBlank()) return san
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "findFirstImageUrlInJson error: ${e.message}")
                }
                return null
            }

            // New: tolerant extractor that takes raw Trefle /search JSON string and returns first image URL found.
            fun extractFirstImageUrlFromTrefleSearchRaw(body: String?): String? {
                if (body.isNullOrBlank()) return null
                try {
                    val root = JsonParser.parseString(body).asJsonObject
                    // if data exists and is array, iterate
                    if (root.has("data") && root.get("data").isJsonArray) {
                        val arr = root.getAsJsonArray("data")
                        for (i in 0 until arr.size()) {
                            val el = arr[i]
                            val found = findFirstImageUrlInJson(el)
                            if (!found.isNullOrBlank()) return sanitizeImageUrl(found)
                        }
                    }

                    // otherwise, scan entire document
                    val foundAny = findFirstImageUrlInJson(root)
                    return sanitizeImageUrl(foundAny)
                } catch (e: Exception) {
                    Log.w(S_TAG, "extractFirstImageUrlFromTrefleSearchRaw parse failed: ${e.message}")
                    return null
                }
            }

            // helper: extract first image url anywhere inside a TreflePlant DTO by serializing to JSON and searching
            // kept for when we already have a typed TreflePlant instance
            fun extractFirstImageFromTrefle(t: TreflePlant?): String? {
                if (t == null) return null
                try {
                    // 1) quick checks on common known fields
                    listOfNotNull(
                        try { t::class.java.getMethod("getImageUrl").invoke(t) as? String } catch (_: Exception) { null },
                        try { t::class.java.getMethod("getImage_url").invoke(t) as? String } catch (_: Exception) { null },
                        try {
                            val ms = try { t::class.java.getMethod("getMainSpecies").invoke(t) } catch (_: Exception) { null }
                            if (ms != null) {
                                try { ms::class.java.getMethod("getImageUrl").invoke(ms) as? String } catch (_: Exception) { null }
                            } else null
                        } catch (_: Exception) { null }
                    ).firstOrNull()?.let { found ->
                        val s = sanitizeImageUrl(found)
                        if (!s.isNullOrBlank()) {
                            Log.d(S_TAG, "extractFirstImageFromTrefle -> quick field hit : $s")
                            return s
                        }
                    }

                    // 2) check main_species.images arrays (if present)
                    try {
                        val ms = try { t::class.java.getMethod("getMainSpecies").invoke(t) } catch (_: Exception) { null }
                        if (ms != null) {
                            val imagesObj = try { ms::class.java.getMethod("getImages").invoke(ms) } catch (_: Exception) { null }
                            if (imagesObj is Collection<*>) {
                                for (entry in imagesObj) {
                                    try {
                                        val imageUrl = entry?.let { en ->
                                            try { en::class.java.getMethod("getImageUrl").invoke(en) as? String } catch (_: Exception) { null }
                                        }
                                        val san = sanitizeImageUrl(imageUrl)
                                        if (!san.isNullOrBlank()) {
                                            Log.d(S_TAG, "extractFirstImageFromTrefle -> main_species.images hit: $san")
                                            return san
                                        }
                                    } catch (_: Exception) { /* ignore per-entry */ }
                                }
                            }
                        }
                    } catch (_: Exception) { /* ignore */ }

                    // 3) Full JSON fallback (defensive): serialize DTO to JSON and search recursively
                    val gson = Gson()
                    val json = try {
                        JsonParser.parseString(gson.toJson(t))
                    } catch (e: Exception) {
                        Log.w(S_TAG, "extractFirstImageFromTrefle gson-serialize failed: ${e.message}")
                        return null
                    }

                    val found = findFirstImageUrlInJson(json)
                    Log.d(S_TAG, "extractFirstImageFromTrefle -> found(raw) : $found")
                    val san = sanitizeImageUrl(found)
                    Log.d(S_TAG, "extractFirstImageFromTrefle -> sanitized : $san")
                    return san
                } catch (e: Exception) {
                    Log.w(S_TAG, "extractFirstImageFromTrefle failed: ${e.message}")
                    return null
                }
            }


            // helper: try to obtain image url for a given name using Trefle first, fallback to search; heavy logging
            suspend fun findImageForName(name: String?): String? {
                if (name.isNullOrBlank()) return null
                Log.d(S_TAG, "findImageForName: start for: '$name'")
                // prepare candidate forms
                val candidates = mutableListOf<String>()
                candidates.add(name)
                // slug-like short form
                val slug = name.replace(Regex("\\s*\\(.*\\)"), "")
                    .replace("×", "")
                    .replace(Regex("[^A-Za-z0-9\\s-]"), "")
                    .trim()
                    .lowercase()
                    .split(Regex("\\s+"))
                    .take(2)
                    .joinToString("-")
                    .replace(Regex("-+"), "-")
                if (slug.isNotBlank()) candidates.add(slug)

                for (cand in candidates) {
                    if (cand.isBlank()) continue
                    Log.d(S_TAG, "findImageForName: trying candidate='$cand'")
                    // try slug lookup first
                    val slugSafe = cand.replace(Regex("[^a-z0-9-]"), "")
                    if (slugSafe.isNotBlank()) {
                        try {
                            val slugResp = trefleService.getPlantBySlug(slugSafe, trefleKey)
                            if (slugResp.isSuccessful) {
                                // attempt raw-body tolerant parse first
                                val rawSlug = try { slugResp.raw().let { r -> r.body?.string() } } catch (_: Exception) { null }
                                val rawImg = extractFirstImageUrlFromTrefleSearchRaw(rawSlug)
                                if (!rawImg.isNullOrBlank()) {
                                    Log.d(S_TAG, "findImageForName: found via slug raw '$slugSafe' -> $rawImg")
                                    return rawImg
                                }

                                // typed fallback
                                try {
                                    val data = slugResp.body()?.data
                                    if (data != null) {
                                        val img = extractFirstImageFromTrefle(data)
                                        if (!img.isNullOrBlank()) {
                                            Log.d(S_TAG, "findImageForName: found via slug typed '$slugSafe' -> $img")
                                            return img
                                        } else {
                                            Log.d(S_TAG, "findImageForName: slug '$slugSafe' returned no image")
                                        }
                                    } else {
                                        Log.d(S_TAG, "findImageForName: slug lookup returned no-data for '$slugSafe' (code=${slugResp.code()})")
                                    }
                                } catch (typedEx: Exception) {
                                    Log.w(S_TAG, "findImageForName: typed parse failed for slug '$slugSafe': ${typedEx.message}")
                                }
                            } else {
                                Log.d(S_TAG, "findImageForName: slug lookup returned no-data for '$slugSafe' (code=${slugResp.code()})")
                            }
                        } catch (e: Exception) {
                            Log.w(S_TAG, "findImageForName: slug lookup exception for '$slugSafe': ${e.message}")
                        }
                    }

                    // fallback: search endpoint
                    try {
                        val searchResp = trefleService.searchPlant(cand, trefleKey)
                        if (searchResp.isSuccessful) {
                            // RAW-first: get raw body string and attempt tolerant extraction to avoid Gson shape issues
                            val raw = try { searchResp.raw().let { r -> r.body?.string() } } catch (_: Exception) { null }
                            val rawImg = extractFirstImageUrlFromTrefleSearchRaw(raw)
                            if (!rawImg.isNullOrBlank()) {
                                Log.d(S_TAG, "findImageForName: found via raw-search '${cand}' -> $rawImg")
                                return rawImg
                            }

                            // Typed fallback: try DTOs if raw didn't yield
                            try {
                                val list = searchResp.body()?.data
                                Log.d(S_TAG, "findImageForName: search '${cand}' returned ${list?.size ?: 0} (typed)")
                                if (!list.isNullOrEmpty()) {
                                    for (entry in list) {
                                        val img = extractFirstImageFromTrefle(entry)
                                        if (!img.isNullOrBlank()) {
                                            Log.d(S_TAG, "findImageForName: found via search '${cand}' -> $img (typed path)")
                                            return img
                                        }
                                    }
                                }
                            } catch (typedEx: Exception) {
                                Log.w(S_TAG, "findImageForName: typed body parse failed for search '$cand': ${typedEx.message}. (typed fallback)")
                                // continue — raw already attempted
                            }

                            // As another fallback, if raw was readable but earlier scan didn't find images in data array,
                            // try scanning the entire raw document once more (already done inside extractor but keep defensive)
                            if (!raw.isNullOrBlank()) {
                                try {
                                    val parsed = JsonParser.parseString(raw)
                                    val found = findFirstImageUrlInJson(parsed)
                                    val san = sanitizeImageUrl(found)
                                    if (!san.isNullOrBlank()) {
                                        Log.d(S_TAG, "findImageForName: found via raw-search entire-doc '${cand}' -> $san")
                                        return san
                                    }
                                } catch (rawEx: Exception) {
                                    Log.w(S_TAG, "findImageForName: raw JSON second-pass failed for '${cand}': ${rawEx.message}")
                                }
                            }

                        } else {
                            Log.d(S_TAG, "findImageForName: search failed for '$cand' code=${searchResp.code()}")
                        }
                    } catch (e: Exception) {
                        Log.w(S_TAG, "findImageForName: search exception for '$cand': ${e.message}")
                    }

                }

                Log.d(S_TAG, "findImageForName: no image found for '$name'")
                return null
            }

            // 1) Try PlantNet synonyms (fast path)
            val syns = plantTop?.synonyms ?: emptyList()
            if (syns.isNotEmpty()) {
                Log.d(S_TAG, "PlantNet synonyms count=${syns.size} -> ${syns.take(6)}")
                for (s in syns.take(6)) {
                    val img = try { findImageForName(s) } catch (e: Exception) {
                        Log.w(S_TAG, "findImageForName crashed for synonym '$s': ${e.message}")
                        null
                    }
                    Log.d(S_TAG, "synonym lookup -> name='$s' image='$img'")
                    similar.add(SimilarSpeciesItem(scientificName = s, score = null, imageUrl = img))
                }
            }

            // 2) If still empty, try PlantNet similar species (the ones shown in plantNet response)
            if (similar.isEmpty()) {
                val pnSimilar = plantTop?.similarSpeciesList() ?: emptyList()
                Log.d(S_TAG, "pnSimilar count=${pnSimilar.size}")
                if (pnSimilar.isNotEmpty()) {
                    for (item in pnSimilar.take(6)) {
                        // Defensive: try top-level scientificName, then species sub-object fields, then fallback to any common name
                        val nameCandidates = mutableListOf<String>()

                        try {
                            item.scientificName?.let { if (it.isNotBlank()) nameCandidates.add(it) }
                        } catch (_: Exception) { /* ignore */ }

                        // If the PlantNet DTO exposes a 'species' object, probe it reflectively
                        try {
                            val speciesObj = try { item::class.java.getMethod("getSpecies").invoke(item) } catch (_: Exception) { null }
                            if (speciesObj != null) {
                                try {
                                    val sName = try { speciesObj::class.java.getMethod("getScientificName").invoke(speciesObj) as? String } catch (_: Exception) { null }
                                    val sNoAuthor = try { speciesObj::class.java.getMethod("getScientificNameWithoutAuthor").invoke(speciesObj) as? String } catch (_: Exception) { null }
                                    val common = try { speciesObj::class.java.getMethod("getCommonNames").invoke(speciesObj) } catch (_: Exception) { null }

                                    if (!sName.isNullOrBlank()) nameCandidates.add(sName)
                                    if (!sNoAuthor.isNullOrBlank()) nameCandidates.add(sNoAuthor)
                                    if (common is Collection<*>) {
                                        common.firstOrNull()?.let { if (it is String && it.isNotBlank()) nameCandidates.add(it) }
                                    }
                                } catch (_: Exception) { Log.d(S_TAG, "pnSimilar: species reflective probe minor failure") }
                            }
                        } catch (_: Exception) { /* ignore reflection errors */ }

                        // last resort: try commonName getter on item
                        try {
                            val commonField = try { item::class.java.getMethod("getCommonName").invoke(item) as? String } catch (_: Exception) { null }
                            if (!commonField.isNullOrBlank()) nameCandidates.add(commonField)
                        } catch (_: Exception) { /* ignore */ }

                        Log.d(S_TAG, "pnSimilar item nameCandidates=${nameCandidates}")

                        // Now try candidates via Trefle to find an image
                        var got: String? = null
                        for (cand in nameCandidates) {
                            if (cand.isBlank()) continue
                            got = try { findImageForName(cand) } catch (e: Exception) {
                                Log.w(S_TAG, "findImageForName threw for '$cand': ${e.message}")
                                null
                            }
                            if (!got.isNullOrBlank()) {
                                Log.d(S_TAG, "pnSimilar -> candidate='$cand' -> image='$got'")
                                break
                            }
                        }

                        // If still null, avoid calling item.imageUrl() directly (might not exist) — keep defensive
                        val label = nameCandidates.firstOrNull() ?: (try { item.scientificName } catch (_: Exception) { null }) ?: "Unknown"
                        Log.d(S_TAG, "pnSimilar adding -> label='$label' score=${try { item.score } catch (_: Exception) { null }} image='$got'")
                        similar.add(SimilarSpeciesItem(scientificName = label, score = try { item.score } catch (_: Exception) { null }, imageUrl = got))
                    }
                }
            }

            // 3) As a last resort, add genus-level or a single placeholder entry (keeps UI stable)
            if (similar.isEmpty()) {
                try {
                    val genus = plantTop?.taxonomy?.get("genus")
                    Log.d(S_TAG, "similar fallback -> genus: $genus")
                    genus?.let { g -> similar.add(SimilarSpeciesItem(scientificName = g, imageUrl = null)) }
                } catch (_: Exception) { }
            }

            Log.d(S_TAG, "Final similar list size=${similar.size} -> ${similar.map { it.scientificName + " | " + it.imageUrl }}")

            val nativeRange = trefle?.nativeRangeFallback()

            IdentificationResult(
                plantNetTop = plantTop,
                treflePlant = trefle,
                wikiExtract = wikiExtract,
                wikiSections = wikiSections,
                careGuide = careWithNotes,
                similarSpecies = similar,
                morphology = morphology,
                nativeRange = nativeRange
            )
        }

    suspend fun fetchTrefleByName(scientificName: String, synonyms: List<String>? = null): TreflePlant? =
        withContext(Dispatchers.IO) {
            fun makeSlugCandidate(name: String): String {
                val clean = name.replace(Regex("\\s*\\(.*\\)"), "")
                    .replace("×", "")
                    .replace(Regex("[^A-Za-z0-9\\s-]"), "")
                    .trim()
                    .lowercase()
                return clean.split(Regex("\\s+")).take(2).joinToString("-").replace(Regex("-+"), "-")
            }

            val tried = mutableSetOf<String>()
            val queue = mutableListOf<String>()
            queue.add(makeSlugCandidate(scientificName))
            queue.add(scientificName)
            synonyms?.forEach {
                queue.add(makeSlugCandidate(it))
                queue.add(it)
            }

            for (candidate in queue) {
                if (candidate.isBlank()) continue
                if (!tried.add(candidate)) continue
                try {
                    val slug = candidate.replace(Regex("[^a-z0-9-]"), "")
                    if (slug.isNotBlank()) {
                        val slugResp = trefleService.getPlantBySlug(slug, trefleKey)
                        if (slugResp.isSuccessful && slugResp.body()?.data != null) {
                            Log.d(TAG, "fetchTrefleByName: slug lookup success for '$slug'")
                            return@withContext slugResp.body()!!.data!!
                        } else {
                            Log.d(TAG, "fetchTrefleByName: slug lookup no-data for '$slug' code=${slugResp.code()}")
                        }
                    }

                    val searchResp = trefleService.searchPlant(candidate, trefleKey)
                    if (searchResp.isSuccessful) {
                        val list = searchResp.body()?.data
                        if (!list.isNullOrEmpty()) {
                            Log.d(TAG, "fetchTrefleByName: search returned ${list.size} for '$candidate' (using first)")
                            return@withContext list[0]
                        } else {
                            Log.d(TAG, "fetchTrefleByName: search returned empty for '$candidate'")
                        }
                    } else {
                        Log.d(TAG, "fetchTrefleByName: search failed for '$candidate' code=${searchResp.code()}")
                    }
                } catch (ex: Exception) {
                    Log.w(TAG, "Exception during Trefle lookup for '$candidate': ${ex.message}")
                }
            }

            try {
                val genus = scientificName.split(Regex("\\s+")).firstOrNull()
                if (!genus.isNullOrBlank() && tried.add(genus)) {
                    val genResp = trefleService.searchPlant(genus, trefleKey)
                    if (genResp.isSuccessful) {
                        val list = genResp.body()?.data
                        if (!list.isNullOrEmpty()) return@withContext list[0]
                    }
                }
            } catch (_: Exception) { }

            Log.d(TAG, "fetchTrefleByName: no trefle match for '$scientificName'")
            null
        }

    /**
     * Fetch summary (lead) and a much larger wiki sections block (mobile-sections preferred).
     * Returns Pair(summary, sections). sections is a larger concatenation of non-lead content where available.
     */
    private suspend fun fetchWikipediaWithLanguageFallback(scientificName: String, synonyms: List<String>? = null): Pair<String?, String?> =
        withContext(Dispatchers.IO) {
            fun makeTitleCandidates(name: String): List<String> {
                val cleaned = name.replace(Regex("\\s*\\(.*\\)"), "").trim()
                val underscore = cleaned.replace("\\s+".toRegex(), "_")
                val simple = underscore.replace(Regex("[^\\p{L}0-9_\\-]"), "")
                val genusSpecies = underscore.split("_").take(2).joinToString("_")
                return listOfNotNull(simple.takeIf { it.isNotBlank() }, genusSpecies.takeIf { it.isNotBlank() })
            }

            val queue = mutableListOf<String>()
            queue.addAll(makeTitleCandidates(scientificName))
            synonyms?.forEach { s -> queue.addAll(makeTitleCandidates(s)) }
            val tried = mutableSetOf<String>()
            val languages = listOf("en", "af", "es", "fr")

            for (lang in languages) {
                for (candidate in queue) {
                    if (candidate.isBlank()) continue
                    val key = "$lang::$candidate"
                    if (!tried.add(key)) continue
                    try {
                        val encoded = URLEncoder.encode(candidate, "UTF-8").replace("+", "%20")
                        // summary endpoint (lead)
                        val summaryUrl = "https://$lang.wikipedia.org/api/rest_v1/page/summary/$encoded"
                        val summaryResp = try { wikiService.getSummary(summaryUrl) } catch (e: Exception) { null }
                        val summaryText = if (summaryResp?.isSuccessful == true) {
                            // ensure small safe size and cleaned text
                            withContext(Dispatchers.Default) {
                                WikiFormatter.cleanHtmlToText(summaryResp.body()?.extract ?: "", maxChars = EXTRACT_MAX)
                            }
                        } else null

                        // mobile-sections endpoint - preferred for sections
                        val mobileUrl = "https://$lang.wikipedia.org/api/rest_v1/page/mobile-sections/$encoded"
                        val mobileResp = try { wikiService.getMobileSections(mobileUrl) } catch (e: Exception) { null }

                        var sectionsText: String? = null

                        if (mobileResp?.isSuccessful == true) {
                            try {
                                val built = withContext(Dispatchers.Default) {
                                    val raw = mobileResp.raw().let { r ->
                                        try {
                                            mobileResp.raw().body?.string()
                                        } catch (e: Exception) {
                                            try { mobileResp.body()?.toString() } catch (_: Exception) { null }
                                        }
                                    }
                                    raw
                                }

                                if (!built.isNullOrBlank()) {
                                    try {
                                        val json = JsonParser.parseString(built).asJsonObject
                                        val remainingJson = if (json.has("remaining")) json.getAsJsonObject("remaining") else null
                                        val sectionsArray = when {
                                            remainingJson != null && remainingJson.has("sections") -> remainingJson.getAsJsonArray("sections")
                                            json.has("sections") -> json.getAsJsonArray("sections")
                                            else -> null
                                        }

                                        if (sectionsArray != null) {
                                            val sb = StringBuilder()
                                            for (s in sectionsArray) {
                                                val so = s.asJsonObject
                                                val title = if (so.has("line")) so.get("line").asString else ""
                                                val textHtml = if (so.has("text")) so.get("text").asString else ""
                                                val clean = WikiFormatter.cleanHtmlToText(textHtml, maxChars = 2000)
                                                if (clean.isNotBlank()) {
                                                    if (title.isNotBlank()) sb.appendLine(title.trim()).appendLine()
                                                    sb.appendLine(clean)
                                                    sb.appendLine()
                                                }
                                                if (sb.length > SECTIONS_MAX) break
                                            }
                                            val out = sb.toString().trim()
                                            if (out.isNotBlank()) sectionsText = out
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "mobile-sections defensive JSON parse failed: ${e.message}")
                                    }
                                }

                            } catch (e: Exception) {
                                Log.w(TAG, "mobile-sections parse failed: ${e.message}")
                            }
                        }

                        // Secondary fallback: use parse endpoint (returns HTML) if mobile-sections didn't yield content
                        if (sectionsText.isNullOrBlank()) {
                            try {
                                val parseUrl = "https://$lang.wikipedia.org/w/api.php?action=parse&page=$encoded&prop=text&format=json"
                                val parseRespRaw = try { wikiService.getRaw(parseUrl) } catch (e: Exception) { null }
                                if (parseRespRaw?.isSuccessful == true) {
                                    try {
                                        val raw = parseRespRaw.body()?.string()
                                        if (!raw.isNullOrBlank()) {
                                            val json = JsonParser.parseString(raw).asJsonObject
                                            if (json.has("parse")) {
                                                val textObj = json.getAsJsonObject("parse")
                                                val html = when {
                                                    textObj.has("text") && textObj.get("text").isJsonObject -> {
                                                        val t = textObj.getAsJsonObject("text")
                                                        if (t.has("*")) t.get("*").asString else null
                                                    }
                                                    textObj.has("text") && textObj.get("text").isJsonPrimitive -> textObj.get("text").asString
                                                    else -> null
                                                }
                                                if (!html.isNullOrBlank()) {
                                                    val cleaned = withContext(Dispatchers.Default) { WikiFormatter.extractBestSectionsFromHtml(html, maxTotalChars = SECTIONS_MAX) }
                                                    if (!cleaned.isNullOrBlank()) {
                                                        sectionsText = cleaned
                                                    }
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "parse-endpoint defensive parse failed: ${e.message}")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "parse-endpoint request failed: ${e.message}")
                            }
                        }

                        // If either summary or sections found, return them
                        if (!summaryText.isNullOrBlank() || !sectionsText.isNullOrBlank()) {
                            Log.d(TAG, "fetchWikipediaWithLanguageFallback -> found for $candidate [$lang]")
                            return@withContext Pair(summaryText, sectionsText)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "wiki lookup exception for $candidate [$lang]: ${e.message}")
                    }
                }
            }
            Pair(null, null)
        }

    // ----- helpers -----
    private fun prepareImageParts(uriList: List<Uri>): List<MultipartBody.Part> {
        val parts = mutableListOf<MultipartBody.Part>()
        uriList.forEachIndexed { idx, uri ->
            val file = File(context.cacheDir, "upload_${System.currentTimeMillis()}_$idx.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            parts.add(MultipartBody.Part.createFormData("images", file.name, requestFile))
        }
        return parts
    }

    // kept for compatibility (but uses improved WikiFormatter)
    private fun stripHtml(html: String?): String {
        if (html.isNullOrBlank()) return ""
        return WikiFormatter.cleanHtmlToText(html, maxChars = EXTRACT_MAX)
    }

    /**
     * Fetch the full mobile-sections representation from Wikipedia for a given title.
     * Returns concatenated section text (excluding trivial sections) or null if not found.
     */
    private suspend fun fetchFullWikipediaSections(scientificName: String, synonyms: List<String>? = null): String? =
        withContext(Dispatchers.IO) {
            // This function reuses the logic in fetchWikipediaWithLanguageFallback — kept for backwards compatibility.
            val pair = fetchWikipediaWithLanguageFallback(scientificName, synonyms)
            return@withContext pair?.second
        }

    // Utility used by buildGeneralGuideText to pick paragraphs not present in other cards
    private fun pickNovelText(fullText: String, exclusions: List<String>, maxChars: Int = 3000): String {
        if (fullText.isBlank()) return ""
        val paragraphs = fullText.split(Regex("\\n{1,}")).map { it.trim() }.filter { it.isNotBlank() }
        val out = StringBuilder()
        for (p in paragraphs) {
            val lower = p.lowercase()
            val isDup = exclusions.any { ex ->
                val e = ex.lowercase()
                e.contains(lower) || lower.contains(e) || commonTokenOverlap(lower, e) > 0.6
            }
            if (!isDup) {
                out.appendLine(p)
                out.appendLine()
                if (out.length > maxChars) break
            }
        }
        return out.toString().trim()
    }

    private fun commonTokenOverlap(a: String, b: String): Double {
        val aset = a.split(Regex("\\W+")).filter { it.isNotBlank() }.toSet()
        val bset = b.split(Regex("\\W+")).filter { it.isNotBlank() }.toSet()
        if (aset.isEmpty() || bset.isEmpty()) return 0.0
        val common = aset.intersect(bset).size.toDouble()
        val denom = maxOf(aset.size, bset.size).toDouble()
        return common / denom
    }

}
