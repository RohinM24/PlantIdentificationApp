package com.example.roleaf.model

// Minimal MediaWiki search response model used for "search" fallback
data class WikiSearchResponse(
    val batchcomplete: String?,
    val query: WikiQuery?
)

data class WikiQuery(
    val search: List<WikiSearchResult>?
)

data class WikiSearchResult(
    val ns: Int?,
    val title: String?,
    val snippet: String?,
    val size: Int?,
    val wordcount: Int?,
    val timestamp: String?
)

