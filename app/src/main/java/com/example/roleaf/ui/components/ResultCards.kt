package com.example.roleaf.ui.components

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import com.example.roleaf.model.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.example.roleaf.util.*
import coil.compose.rememberAsyncImagePainter
import com.example.roleaf.ui.theme.RoleafPrimary
import com.example.roleaf.ui.theme.RoleafOnPrimary

private const val UI_TAG = "SIMILAR_UI"

/**
 * Sanitise and normalise potential image URLs.
 * - Handles quoted strings, //protocol-less, http->https
 * - Accepts common image extensions; allows certain CDN/host heuristics
 * - Attempts to extract a URL if the field accidentally contains JSON or inline object text
 */
private fun sanitizeImageUrl(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    var url = raw.trim()

    // if value looks like JSON string/object with a url field, try to extract (very tolerant)
    if ((url.startsWith("{") && url.contains("url")) || url.contains("\"url\":") || url.contains("\"image_url\":")) {
        Regex("\"(image_url|url|src|original_url)\"\\s*[:=]\\s*\"([^\"]+)\"").find(url)?.let { m ->
            val candidate = m.groupValues.getOrNull(2)
            if (!candidate.isNullOrBlank()) url = candidate
        }
    }

    // if quoted as a single string
    if ((url.startsWith("\"") && url.endsWith("\"")) || (url.startsWith("'") && url.endsWith("'"))) {
        url = url.substring(1, url.length - 1)
    }

    // protocol-less -> https
    if (url.startsWith("//")) url = "https:$url"
    if (url.startsWith("http://")) url = url.replaceFirst("http://", "https://")

    // if bare host/path like "bs.plantnet.org/image/..." -> prefix https://
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        if (url.matches(Regex("^[\\w\\.-]+(/[\\w\\./%\\-\\?=&+]*)?$"))) {
            url = "https://$url"
        }
    }

    val lower = url.lowercase().trim()

    // quick accept if URL ends with common image extension (allow query strings)
    if (lower.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp|svg)(\\?.*)?$"))) return url

    // accept if it contains known CDN/host fragments frequently used for images
    val allowFragments = listOf("upload", "cdn", "images", "imgur", "wikimedia", "plantnet", "cloudfront", "bs.plantnet", "d2seqvvyy")
    if (allowFragments.any { lower.contains(it) }) return url

    // fallback: if it's an https url at all, return it
    if (url.startsWith("https://")) return url

    // otherwise reject
    return null
}

/** Utility to normalize list of candidate strings to first good sanitized URL */
private fun firstSanitized(vararg candidates: String?): String? {
    for (c in candidates) {
        val s = sanitizeImageUrl(c)
        if (!s.isNullOrBlank()) return s
    }
    return null
}

@Composable
fun PlantIdentifiedCard(
    commonName: String,
    scientificName: String,
    confidencePercent: Int,
    plantNetResult: PlantNetResult?,
    trefle: TreflePlant?,
    fallbackLocalUri: Uri?,                  // <-- NEW parameter
    onTapSynonym: (String) -> Unit,
    onCopyScientificName: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1F18)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Plant Identified",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Common: $commonName", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFBFEBC1))
                    Text(
                        text = "Scientific: $scientificName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFBFEBC1),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { onCopyScientificName(scientificName) }) {
                    Icon(imageVector = Icons.Default.CopyAll, contentDescription = "Copy scientific name", tint = Color(0xFFBFEBC1))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(text = "Confidence: ${confidencePercent.coerceIn(0, 100)}%", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF98F29B))

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                // Build Trefle candidates only (we will prefer Trefle images first)
                val trefleCandidateList = mutableListOf<String?>()
                try {
                    // 1) images list on trefle (if present) — iterate and prefer any reasonable field
                    trefle?.images?.forEach { img ->
                        val rawCandidates = listOf(
                            img.url,
                            img.image_url,
                            img.original_url,
                            img.src,
                            img.large_url,
                            img.thumbnail_url,
                            img.source,
                            img.filename
                        )
                        rawCandidates.firstOrNull { !it.isNullOrBlank() }?.let { trefleCandidateList.add(it) }
                    }

                    // 2) top-level trefle.image_url
                    if (!trefle?.image_url.isNullOrBlank()) trefleCandidateList.add(trefle?.image_url)
                } catch (ex: Exception) {
                    Log.w(UI_TAG, "error enumerating trefle images: ${ex.message}")
                }

                // sanitize and unique preserving order (Trefle-only)
                val sanitizedTrefle = trefleCandidateList.mapNotNull { sanitizeImageUrl(it) }.distinct()
                val treflePreviewUrl = sanitizedTrefle.firstOrNull()

                // Prepare PlantNet first as last-resort (sanitized)
                val pnFirst = plantNetResult?.imageUrls()?.firstOrNull()
                val pnFirstSan = sanitizeImageUrl(pnFirst)

                Log.d(UI_TAG, "PlantIdentifiedCard: trefleCandidates=${trefleCandidateList.take(6)}, trefleChosen=$treflePreviewUrl, fallbackLocalUri=$fallbackLocalUri, pnFallback=$pnFirstSan")

                if (!treflePreviewUrl.isNullOrBlank()) {
                    // Trefle image available -> show that (highest priority)
                    Card(shape = RoundedCornerShape(10.dp), modifier = Modifier.size(120.dp)) {
                        AsyncImage(
                            model = treflePreviewUrl,
                            contentDescription = "Preview image (Trefle)",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (fallbackLocalUri != null) {
                    // No Trefle images -> use user's uploaded Uri (second priority)
                    Card(shape = RoundedCornerShape(10.dp), modifier = Modifier.size(120.dp)) {
                        AsyncImage(
                            model = fallbackLocalUri,
                            contentDescription = "Preview image (uploaded)",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (!pnFirstSan.isNullOrBlank()) {
                    // Last resort: PlantNet first image (existing behavior)
                    Card(shape = RoundedCornerShape(10.dp), modifier = Modifier.size(120.dp)) {
                        AsyncImage(
                            model = pnFirstSan,
                            contentDescription = "Preview image (PlantNet)",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0C1E14)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No image", color = Color(0xFFB8CBB3))
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    plantNetResult?.let { pn ->
                        val organ = pn.detectedOrgan()
                        if (!organ.isNullOrBlank()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1E14)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "Detected organ:", color = Color(0xFFBFEBC1))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = organ.replaceFirstChar { it.uppercaseChar() }, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    val commonNames = plantNetResult?.species?.commonNames ?: emptyList()
                    if (commonNames.isNotEmpty()) {
                        Text(text = "Common names", style = MaterialTheme.typography.labelSmall, color = Color(0xFFBFEBC1))
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(commonNames) { name ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    tonalElevation = 2.dp,
                                    color = RoleafPrimary // simpler API for Surface background
                                ) {
                                    Text(
                                        text = name,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        color = Color.Black,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val syns = plantNetResult?.synonyms ?: emptyList()
                    if (syns.isNotEmpty()) {
                        Text(text = "Synonyms", style = MaterialTheme.typography.labelSmall, color = Color(0xFFBFEBC1))
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(syns) { s ->
                                Card(
                                    modifier = Modifier.clickable { onTapSynonym(s) },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF2E8B57))
                                ) {
                                    Text(text = s, modifier = Modifier.padding(8.dp), color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // More images from PlantNet (but exclude any that match the chosen trefle image)
            val chosenPreview = run {
                val candidates = mutableListOf<String?>()
                try {
                    trefle?.images?.forEach { img ->
                        val firstFound = listOf(
                            img.url,
                            img.image_url,
                            img.original_url,
                            img.src,
                            img.large_url,
                            img.thumbnail_url,
                            img.source,
                            img.filename
                        ).firstOrNull { !it.isNullOrBlank() }
                        if (!firstFound.isNullOrBlank()) candidates.add(firstFound)
                    }
                    if (!trefle?.image_url.isNullOrBlank()) candidates.add(trefle?.image_url)
                } catch (_: Exception) {}
                candidates.mapNotNull { sanitizeImageUrl(it) }.firstOrNull()
            }

            val pnImages = plantNetResult?.imageUrls()?.mapNotNull { sanitizeImageUrl(it) } ?: emptyList()
            val moreImages = if (pnImages.isEmpty()) emptyList() else pnImages.filter { it != chosenPreview }

            if (moreImages.isNotEmpty()) {
                Text(text = "More images (PlantNet)", style = MaterialTheme.typography.labelSmall, color = Color(0xFFBFEBC1))
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(moreImages) { url ->
                        Card(shape = RoundedCornerShape(8.dp), modifier = Modifier.size(88.dp)) {
                            AsyncImage(model = url, contentDescription = "sample image", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            val taxonomy = plantNetResult?.taxonomy
            if (!taxonomy.isNullOrEmpty()) {
                Text(text = "Taxonomy", style = MaterialTheme.typography.titleSmall, color = Color(0xFFBFEBC1))
                Spacer(modifier = Modifier.height(6.dp))
                val order = listOf("kingdom", "phylum", "class", "order", "family", "genus", "species")
                Column {
                    order.forEach { key ->
                        val v = taxonomy[key]
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("${key.replaceFirstChar { it.uppercaseChar() }}:", modifier = Modifier.weight(0.4f), color = Color(0xFFBFEBC1))
                            Text(v ?: "—", modifier = Modifier.weight(0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlantNetGallery(imageUrls: List<String>?) {
    if (imageUrls.isNullOrEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Sample images", style = MaterialTheme.typography.titleSmall, color = Color(0xFFBFEBC1))
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(imageUrls) { url ->
                val s = sanitizeImageUrl(url)
                if (!s.isNullOrBlank()) {
                    Card(shape = RoundedCornerShape(10.dp), modifier = Modifier.size(110.dp)) {
                        AsyncImage(
                            model = s,
                            contentDescription = "PlantNet sample image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TaxonomyTreeSimple(taxonomy: Map<String, String>?) {
    if (taxonomy.isNullOrEmpty()) return
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1F18)), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Taxonomy", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            val order = listOf("kingdom", "phylum", "class", "order", "family", "genus", "species")
            order.forEach { key ->
                val value = taxonomy[key]
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("${key.replaceFirstChar { it.uppercaseChar() }}:", modifier = Modifier.weight(0.35f), color = Color(0xFFBFEBC1))
                    Text(value ?: "—", modifier = Modifier.weight(0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun WikiCardDark(text: String?, modifier: Modifier = Modifier, onCopy: ((String) -> Unit)? = null) {
    if (text.isNullOrBlank()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1F18)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier
            .padding(12.dp)
            .fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Plant Description", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(modifier = Modifier.weight(1f))
                if (onCopy != null) {
                    IconButton(onClick = { onCopy(text) }) {
                        Icon(imageVector = Icons.Default.CopyAll, contentDescription = "Copy description", tint = Color(0xFFBFEBC1))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            val scrollState = rememberScrollState()
            Column(modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp, max = 220.dp)
                .verticalScroll(scrollState)
            ) {
                Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFBFEBC1))
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
fun CareGuideCard(careGuide: CareGuide) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1F18)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Care Guide", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))

            @Composable
            fun row(label: String, value: String?) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("$label:", modifier = Modifier.weight(0.35f), color = Color(0xFFBFEBC1))
                    Text(value ?: "—", modifier = Modifier.weight(0.65f), color = Color.White)
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            row("Watering", careGuide.watering)
            row("Light", careGuide.light)
            row("Soil", careGuide.soil)
            row("Temperature", careGuide.temperature)

            if (!careGuide.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Notes", style = MaterialTheme.typography.labelSmall, color = Color(0xFFBFEBC1))
                Text(careGuide.notes ?: "", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFBFEBC1))
            }
        }
    }
}

@Composable
fun NativeRangeCard(nativeRange: NativeRange) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1F18)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Where it comes from", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            if (!nativeRange.nativeList.isNullOrEmpty()) {
                Text(nativeRange.summary ?: nativeRange.nativeList.joinToString(", "), color = Color(0xFFBFEBC1))
            } else {
                Text("No native range available", color = Color(0xFFBFEBC1))
            }
        }
    }
}
