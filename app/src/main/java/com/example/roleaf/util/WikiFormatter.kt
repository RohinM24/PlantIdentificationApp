package com.example.roleaf.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Locale

/**
 * Small helper to clean/parse HTML from Wikipedia pages.
 * Uses Jsoup to decode entities, remove reference marks like [1], and extract
 * meaningful sections. Returns safe-sized, cleaned plain-text.
 *
 * NOTE: Add to build.gradle:
 * implementation 'org.jsoup:jsoup:1.16.1'
 */

object WikiFormatter {

    // remove [1], [2], etc and normalize whitespace
    private fun removeReferencesAndNormalize(s: String): String {
        // remove bracketed numeric references like [1], [2], [a], [citation needed]
        var out = s.replace(Regex("\\[\\s*\\d+\\s*]"), " ")
        out = out.replace(Regex("\\[citation needed\\]", RegexOption.IGNORE_CASE), " ")
        out = out.replace(Regex("\\[.*?]"), " ")
        out = out.replace(Regex("\\s+"), " ").trim()
        return out
    }

    /**
     * Clean a piece of HTML to plain text, decode entities, remove refs.
     * maxChars: cap the returned string.
     */
    fun cleanHtmlToText(html: String, maxChars: Int = 2000): String {
        if (html.isBlank()) return ""
        return try {
            val doc: Document = Jsoup.parse(html)
            // remove tables, scripts, styles which often introduce noise
            doc.select("table, script, style, noscript, sup.reflist, .reference").remove()
            val text = doc.text()
            val cleaned = removeReferencesAndNormalize(text)
            if (cleaned.length > maxChars) cleaned.take(maxChars).trimEnd() + "…" else cleaned
        } catch (e: Exception) {
            // fallback naive
            val t = html.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
            if (t.length > maxChars) t.take(maxChars).trimEnd() + "…" else t
        }
    }

    /**
     * Try to extract meaningful sections from big HTML (parse endpoint HTML).
     * Picks headings + paragraphs in a readable order and returns joined text.
     *
     * maxTotalChars caps the overall output size to protect the UI.
     */
    fun extractBestSectionsFromHtml(html: String, maxTotalChars: Int = 8000): String {
        if (html.isBlank()) return ""
        return try {
            val doc = Jsoup.parse(html)
            // remove noisy content
            doc.select("table, script, style, nav, .toc, .reference, sup.reference").remove()

            val sb = StringBuilder()
            // priority headings to prefer
            val priorities = listOf("description", "distribution", "habitat", "ecology", "uses", "cultivation", "growth", "characteristics", "identification")
            // find top-level headings and associated paragraphs
            val headings = doc.select("h1, h2, h3, h4, h5, h6")
            if (headings.isEmpty()) {
                // fallback: take first meaningful paragraphs
                val ps = doc.select("p").map { it.text() }.filter { it.isNotBlank() }
                for (p in ps) {
                    val cleaned = removeReferencesAndNormalize(p)
                    if (cleaned.isNotBlank()) {
                        sb.appendLine(cleaned)
                        sb.appendLine()
                    }
                    if (sb.length > maxTotalChars) break
                }
                return sb.toString().trim()
            }

            // collect heading -> paragraphs map
            val collected = mutableListOf<Pair<String, String>>()
            for (h in headings) {
                val title = h.text().trim()
                if (title.isBlank()) continue
                // look for next siblings up to the next heading
                val content = StringBuilder()
                var sib: Element? = h.nextElementSibling()
                while (sib != null && sib.tagName().lowercase(Locale.getDefault()).let { it !in listOf("h1","h2","h3","h4","h5","h6") }) {
                    if (sib.tagName().equals("p", ignoreCase = true)) {
                        val t = sib.text()
                        if (t.isNotBlank()) {
                            content.appendLine(removeReferencesAndNormalize(t))
                            content.appendLine()
                        }
                    } else {
                        // sometimes paragraphs are nested in divs
                        val paragraphs = sib.select("p").map { it.text() }.filter { it.isNotBlank() }
                        paragraphs.forEach { pTxt ->
                            content.appendLine(removeReferencesAndNormalize(pTxt))
                            content.appendLine()
                        }
                    }
                    sib = sib.nextElementSibling()
                }
                val c = content.toString().trim()
                if (c.isNotBlank()) collected.add(title to c)
            }

            // Try to add high priority headings first
            val used = mutableSetOf<String>()
            for (prio in priorities) {
                for ((title, body) in collected) {
                    if (used.contains(title)) continue
                    if (title.lowercase(Locale.getDefault()).contains(prio)) {
                        sb.appendLine(title.trim())
                        sb.appendLine()
                        sb.appendLine(body.trim())
                        sb.appendLine()
                        used.add(title)
                        if (sb.length > maxTotalChars) break
                    }
                }
                if (sb.length > maxTotalChars) break
            }

            // then append other collected headings up to cap
            if (sb.length < maxTotalChars) {
                for ((title, body) in collected) {
                    if (used.contains(title)) continue
                    sb.appendLine(title.trim())
                    sb.appendLine()
                    sb.appendLine(body.trim())
                    sb.appendLine()
                    if (sb.length > maxTotalChars) break
                }
            }

            // If still empty, fall back to body paragraphs
            if (sb.isBlank()) {
                val ps = doc.select("p").map { it.text() }.filter { it.isNotBlank() }
                for (p in ps) {
                    val cleaned = removeReferencesAndNormalize(p)
                    if (cleaned.isNotBlank()) {
                        sb.appendLine(cleaned)
                        sb.appendLine()
                    }
                    if (sb.length > maxTotalChars) break
                }
            }

            val out = sb.toString().trim()
            if (out.length > maxTotalChars) out.take(maxTotalChars).trimEnd() + "…" else out
        } catch (e: Exception) {
            // fallback simple strip
            cleanHtmlToText(html, maxTotalChars)
        }
    }
}


