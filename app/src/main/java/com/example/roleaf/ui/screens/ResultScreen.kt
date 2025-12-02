package com.example.roleaf.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.example.roleaf.ui.components.*
import com.example.roleaf.ui.viewmodel.MainViewModel
import com.example.roleaf.model.CareGuide
import com.example.roleaf.model.TreflePlant
import com.example.roleaf.util.detectedOrgan
import com.example.roleaf.util.similarSpeciesList
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.max
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CopyAll
import com.example.roleaf.util.nativeRangeFallback
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color


private const val TAG = "ResultScreen"

@Composable
fun ResultScreen(
    navController: NavHostController,
    viewModel: MainViewModel
) {
    val uiState by viewModel.ui.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // pick uploaded image preference list (flower first etc.)
    val preferredOrder = listOf("flower", "leaf", "fruit", "bark", "habit", "other")
    val uploadedUriString: String? = remember(uiState.organUris) {
        preferredOrder.mapNotNull { key -> uiState.organUris[key]?.toString() }
            .firstOrNull()
            ?: uiState.organUris.values.firstOrNull()?.toString()
    }

    val uploadedImageModel: Any? = remember(uploadedUriString) {
        when {
            uploadedUriString == null -> null
            uploadedUriString.startsWith("content://") || uploadedUriString.startsWith("file://") ->
                runCatching { Uri.parse(uploadedUriString) }.getOrNull()
            else -> uploadedUriString
        }
    }

    // --- NEW: the Uri? fallback to pass down to PlantIdentifiedCard ---
    val firstUserUri: Uri? = uploadedImageModel as? Uri

    val confidencePercent: Int = uiState.plantNetResult?.bestScorePercent() ?: 0

    val commonName = uiState.treflePlant?.common_name
        ?: uiState.plantNetResult?.primaryCommonName()
        ?: "Unknown"

    val scientificName = uiState.treflePlant?.scientific_name
        ?: ui_state_scientificFallback(uiState.plantNetResult)
        ?: "Unknown"

    val screenScroll = rememberScrollState()
    var screenHeightPx by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { screenHeightPx = it.height }
                .verticalScroll(screenScroll)
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .navigationBarsPadding()
                .background(MaterialTheme.colorScheme.background)
            ,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            val previewSize = 260.dp
            val ringStroke = 12.dp

            Box(
                modifier = Modifier
                    .size(previewSize)
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                // pick big preview: uploaded preferred, else trefle top-level image_url, else plantnet first
                val bigPreviewModel: Any? = remember(uiState.treflePlant, uiState.plantNetResult, uploadedImageModel) {
                    uploadedImageModel
                        ?: uiState.treflePlant?.image_url
                        ?: uiState.plantNetResult?.imageUrls()?.firstOrNull()
                }

                PreviewImageWithAlignedRing(
                    imageModel = bigPreviewModel,
                    confidencePercent = confidencePercent,
                    previewSize = previewSize,
                    ringStroke = ringStroke
                )

                Text(
                    text = "${confidencePercent.coerceIn(0, 100)}%",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pass trefle & plantNet so PlantIdentifiedCard can deterministically choose trefle.image_url first
            PlantIdentifiedCard(
                commonName = commonName,
                scientificName = scientificName,
                confidencePercent = confidencePercent,
                plantNetResult = uiState.plantNetResult,
                trefle = ui_state_trefleOrNull(uiState),
                fallbackLocalUri = firstUserUri, // <-- NEW parameter passed here
                onTapSynonym = { syn -> viewModel.retryTrefleLookupWithName(syn) },
                onCopyScientificName = { name ->
                    clipboard.setText(AnnotatedString(name))
                    Toast.makeText(context, "Scientific name copied", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            PlantNetGallery(imageUrls = uiState.plantNetResult?.imageUrls())

            Spacer(modifier = Modifier.height(12.dp))

            TaxonomyTreeSimple(taxonomy = uiState.plantNetResult?.taxonomy)

            Spacer(modifier = Modifier.height(6.dp))

            WikiCardDark(
                text = uiState.wikiText,
                modifier = Modifier.fillMaxWidth(),
                onCopy = { t ->
                    clipboard.setText(AnnotatedString(t))
                    Toast.makeText(context, "Description copied", Toast.LENGTH_SHORT).show()
                }
            )

            // ---------- extras from UiState (general guide + others) ----------
            if (uiState.careGuide != null ||
                uiState.nativeRange != null ||
                uiState.treflePlant != null ||
                uiState.plantNetResult != null
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Debug logging to help identify upstream data issues
                LaunchedEffect(uiState.treflePlant, uiState.plantNetResult, uiState.careGuide) {
                    Log.d(TAG, "treflePlant present? ${uiState.treflePlant != null}")
                    Log.d(TAG, "plantNetResult present? ${uiState.plantNetResult != null}")
                    Log.d(TAG, "uiState.careGuide present? ${uiState.careGuide != null}")
                }

                // Build a "General Guide" as structured pieces (heading:Boolean, text:String)
                val generalPieces: List<Pair<Boolean, String>>? = remember(
                    uiState.treflePlant,
                    uiState.plantNetResult,
                    uiState.wikiExtra,
                    uiState.careGuide
                ) {
                    buildGeneralGuidePieces(
                        trefle = uiState.treflePlant,
                        plantNet = uiState.plantNetResult,
                        wikiExtra = uiState.wikiExtra,
                        existingCare = uiState.careGuide
                    )
                }

                // Prepare plain-text version for copy-to-clipboard
                val generalPlainText = remember(generalPieces) {
                    generalPiecesToText(generalPieces ?: emptyList())
                }

                // Only show the themed card if we have something useful
                if (!generalPieces.isNullOrEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "General Guide",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    // copy assembled text
                                    if (generalPlainText.isNotBlank()) {
                                        clipboard.setText(AnnotatedString(generalPlainText))
                                        Toast.makeText(context, "General guide copied", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Nothing to copy", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(imageVector = Icons.Default.CopyAll, contentDescription = "Copy General Guide", tint = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            val scroll = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // make the general guide visually bounded and scrollable internally
                                    .heightIn(min = 40.dp, max = 320.dp)
                                    .verticalScroll(scroll)
                            ) {
                                // Render structured pieces: headings bold + slightly larger; bodies normal.
                                generalPieces.forEachIndexed { _, piece ->
                                    val (isHeading, txt) = piece
                                    if (isHeading) {
                                        Text(
                                            text = txt.trim(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    } else {
                                        Text(
                                            text = txt.trim(),
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.secondary,
                                            lineHeight = 18.sp
                                        )
                                    }
                                    // paragraph spacing
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                val native = uiState.nativeRange ?: uiState.treflePlant?.nativeRangeFallback()
                native?.let { nr ->
                    NativeRangeCard(nr)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // --------- START OVER button (matches theme) ----------
            Spacer(modifier = Modifier.height(12.dp))

            val buttonModifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)

            Button(
                onClick = {
                    // 1) Reset viewmodel state (clears results & sets screen = Input)
                    viewModel.resetToInput()
                },
                modifier = buttonModifier,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(text = "Start Over", style = MaterialTheme.typography.titleMedium)
            }


            Spacer(modifier = Modifier.height(12.dp))

            Spacer(modifier = Modifier.weight(1f))
        }

        // small custom scrollbar
        val max = screenScroll.maxValue
        if (max > 0 && screenHeightPx > 0) {
            val density = LocalDensity.current
            val containerPx = screenHeightPx.toFloat()
            val contentHeightPx = containerPx + max.toFloat()
            val minThumbPx = with(density) { 24.dp.toPx() }
            val thumbHeightPx = max(minThumbPx, (containerPx / contentHeightPx) * containerPx)
            val availablePx = containerPx - thumbHeightPx
            val fraction = if (max > 0) screenScroll.value.toFloat() / max.toFloat() else 0f
            val thumbOffsetPx = fraction * availablePx

            // HOIST colors for use inside Canvas (non-composable draw scope)
            val scrollbarBgColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f)
            val scrollbarThumbColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f)

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = 4.dp)
                    .align(Alignment.CenterEnd)
                    .width(6.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = scrollbarBgColor,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                    )
                    drawRoundRect(
                        color = scrollbarThumbColor,
                        topLeft = Offset(x = 0f, y = thumbOffsetPx),
                        size = Size(width = size.width, height = thumbHeightPx),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                    )
                }
            }
        }
    }
}

/* small helper fallbacks to avoid import noise when pasting */
private fun ui_state_scientificFallback(pn: com.example.roleaf.model.PlantNetResult?): String? {
    return pn?.species?.scientificNameWithoutAuthor ?: pn?.species?.scientificName
}

private fun ui_state_trefleOrNull(uiState: com.example.roleaf.ui.viewmodel.UiState): com.example.roleaf.model.TreflePlant? {
    return uiState.treflePlant
}

/* Full PreviewImageWithAlignedRing implementation */
@Composable
private fun PreviewImageWithAlignedRing(
    imageModel: Any?,
    confidencePercent: Int,
    previewSize: Dp,
    ringStroke: Dp
) {
    // subtle pulsing animation for the sweep
    val infinite = rememberInfiniteTransition()
    val pulse by infinite.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val sweepAngle = (confidencePercent.coerceIn(0, 100) / 100f) * 360f
    val strokePx = with(LocalDensity.current) { ringStroke.toPx() }

    // HOIST colors from MaterialTheme (composable scope)
    val bgRingColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
    val sweepColors = listOf(
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.primaryContainer
    )
    val markerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)

    Box(
        modifier = Modifier.size(previewSize),
        contentAlignment = Alignment.Center
    ) {
        // inner circular image background (canvas will draw ring over the full size)
        val innerSizeDp = previewSize - (ringStroke * 2f)
        Box(
            modifier = Modifier
                .size(innerSizeDp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (imageModel != null) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = "Uploaded plant preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = size.minDimension
            val radius = diameter / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // subtle background ring
            drawCircle(
                color = bgRingColor,
                radius = radius - strokePx / 2f,
                center = center,
                style = Stroke(width = strokePx)
            )

            // animated sweep based on confidence
            val sweep = sweepAngle * pulse
            drawArc(
                brush = Brush.sweepGradient(sweepColors),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - radius + strokePx / 2f, center.y - radius + strokePx / 2f),
                size = Size((radius - strokePx / 2f) * 2f, (radius - strokePx / 2f) * 2f),
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // small end marker using hoisted markerColor
            val endAngleRad = Math.toRadians((-90.0 + sweep).toDouble())
            val markerRadius = strokePx * 0.6f
            val endX = center.x + (radius - strokePx) * cos(endAngleRad).toFloat()
            val endY = center.y + (radius - strokePx) * sin(endAngleRad).toFloat()
            drawCircle(
                color = markerColor,
                radius = markerRadius,
                center = Offset(endX, endY)
            )
        }
    }
}

/* Helper: check if a CareGuide is empty */
private fun isCareGuideEmpty(cg: CareGuide?): Boolean {
    if (cg == null) return true
    return listOf(cg.watering, cg.light, cg.soil, cg.temperature, cg.notes).all { it.isNullOrBlank() }
}

/* Convert structured pieces to a single plain text for copying */
private fun generalPiecesToText(pieces: List<Pair<Boolean, String>>): String {
    val sb = StringBuilder()
    for ((isHeading, txt) in pieces) {
        if (isHeading) {
            sb.appendLine(txt.trim())
        } else {
            sb.appendLine(txt.trim())
        }
        sb.appendLine()
    }
    return sb.toString().trim()
}

/* buildGeneralGuidePieces and removeReferencesBlock — keep your original implementation */
private fun buildGeneralGuidePieces(
    trefle: TreflePlant?,
    plantNet: com.example.roleaf.model.PlantNetResult?,
    wikiExtra: String?,
    existingCare: CareGuide?
): List<Pair<Boolean, String>>? {
    // (same implementation you already have)
    val pieces = mutableListOf<Pair<Boolean, String>>()
    existingCare?.let { cg ->
        val cgParts = mutableListOf<String>()
        cg.watering?.let { cgParts.add("Watering: ${it.trim()}") }
        cg.light?.let { cgParts.add("Light: ${it.trim()}") }
        cg.soil?.let { cgParts.add("Soil: ${it.trim()}") }
        cg.temperature?.let { cgParts.add("Temperature: ${it.trim()}") }
        if (cgParts.isNotEmpty()) {
            pieces.add(false to cgParts.joinToString("\n"))
        }
    }

    trefle?.let { t ->
        val tParts = mutableListOf<String>()
        if (!t.scientific_name.isNullOrBlank()) tParts.add("Scientific: ${t.scientific_name}")
        if (!t.family.isNullOrBlank() && !t.family.equals("Unknown", ignoreCase = true)) tParts.add("Family: ${t.family}")
        t.genus?.name?.let { if (it.isNotBlank()) tParts.add("Genus: $it") }

        try {
            val nativeList = t.distributions?.native?.mapNotNull { it.name } ?: emptyList()
            val introList = t.distributions?.introduced?.mapNotNull { it.name } ?: emptyList()
            if (nativeList.isNotEmpty()) tParts.add("Native: ${nativeList.take(6).joinToString(", ")}")
            else if (introList.isNotEmpty()) tParts.add("Introduced: ${introList.take(6).joinToString(", ")}")
        } catch (_: Exception) { }

        try {
            val g = t.growth
            if (g != null) {
                val growParts = mutableListOf<String>()
                if (!g.light.isNullOrBlank()) growParts.add("Light: ${g.light}")
                if (!g.soil_texture.isNullOrBlank()) growParts.add("Soil texture: ${g.soil_texture}")
                if (!g.soil_nutriments.isNullOrBlank()) growParts.add("Soil nutrients: ${g.soil_nutriments}")
                if (g.minimum_temperature?.deg_c != null || g.maximum_temperature?.deg_c != null) {
                    val min = g.minimum_temperature?.deg_c?.toInt()
                    val max = g.maximum_temperature?.deg_c?.toInt()
                    growParts.add("Temperature: " + listOfNotNull(min?.toString(), max?.toString()).joinToString("–") + " °C")
                }
                if (!g.description.isNullOrBlank()) {
                    val s = g.description.trim()
                    growParts.add("Growth note: ${if (s.length > 180) s.take(180).trimEnd() + "…" else s}")
                }
                if (growParts.isNotEmpty()) tParts.add(growParts.joinToString("; "))
            }
        } catch (_: Exception) { }

        if (tParts.isNotEmpty()) pieces.add(false to tParts.joinToString("\n"))
    }

    plantNet?.let { pn ->
        val pnParts = mutableListOf<String>()
        pn.detectedOrgan()?.let { pnParts.add("Detected organ: ${it.replaceFirstChar { c -> c.uppercaseChar() }}") }
        pn.taxonomy?.let { tx ->
            val fam = tx["family"]
            val gen = tx["genus"]
            val sp = tx["species"]
            val txList = listOfNotNull(fam?.let { "Family: $it" }, gen?.let { "Genus: $it" }, sp?.let { "Species: $it" })
            if (txList.isNotEmpty()) pnParts.add(txList.joinToString(", "))
        }
        val syns = pn.synonyms ?: emptyList()
        if (syns.isNotEmpty()) pnParts.add("Synonyms: " + syns.take(6).joinToString(", "))
        val similar = pn.similarSpeciesList().take(4).map { it.scientificName }
        if (similar.isNotEmpty()) pnParts.add("Similar: " + similar.joinToString(", "))

        if (pnParts.isNotEmpty()) pieces.add(false to pnParts.joinToString("\n"))
    }

    if (!wikiExtra.isNullOrBlank()) {
        val raw = removeReferencesBlock(wikiExtra)
        val cleaned = raw.trim()
            .replace(Regex("\\r\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .replace(Regex("[ \t]+\n"), "\n")
            .trim()
        val paras = cleaned.split(Regex("\\n{2,}|\\n")).map { it.trim() }.filter { it.isNotBlank() }
        for (p in paras) {
            val words = p.split(Regex("\\s+")).filter { it.isNotBlank() }
            val isShort = p.length <= 60 && words.size <= 10
            if (isShort) {
                val heading = p.split(Regex("\\s+")).joinToString(" ") { w ->
                    w.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() }
                }
                pieces.add(true to heading)
            } else {
                pieces.add(false to p)
            }
        }
    }

    return if (pieces.isEmpty()) null else pieces
}

private fun removeReferencesBlock(text: String): String {
    val patterns = listOf(
        Regex("(?im)^={2,}\\s*references\\s*={2,}"),
        Regex("(?im)^={2,}\\s*external links\\s*={2,}"),
        Regex("(?im)^={2,}\\s*notes\\s*={2,}"),
        Regex("(?im)^={2,}\\s*footnotes\\s*={2,}"),
        Regex("(?im)^references\\s*$")
    )
    var cutIndex: Int? = null
    for (p in patterns) {
        val m = p.find(text)
        if (m != null) {
            cutIndex = m.range.first
            break
        }
    }
    return if (cutIndex != null && cutIndex > 20) {
        text.substring(0, cutIndex).trim()
    } else {
        text
    }
}
