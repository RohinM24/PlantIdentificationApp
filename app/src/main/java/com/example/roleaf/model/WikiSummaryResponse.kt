package com.example.roleaf.model

data class WikiSummaryResponse(
    val title: String? = null,
    val extract: String? = null,
    val description: String? = null,
    val content_urls: ContentUrls? = null,
    val thumbnail: Thumbnail? = null
)

data class ContentUrls(
    val desktop: Desktop? = null,
    val mobile: Mobile? = null
)

data class Desktop(val page: String? = null)
data class Mobile(val page: String? = null)

data class Thumbnail(
    val source: String? = null,
    val width: Int? = null,
    val height: Int? = null
)
data class WikiMobileSectionsResponse(
    val lead: WikiSectionContainer? = null,
    val remaining: WikiSectionContainer? = null
)

data class WikiSectionContainer(
    val sections: List<WikiSection>? = null
)

data class WikiSection(
    val line: String? = null,   // section title
    val text: String? = null    // HTML content (we'll strip tags)
)


