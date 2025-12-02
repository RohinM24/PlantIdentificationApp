// app/src/main/java/com/example/roleaf/api/WikipediaApi.kt
package com.example.roleaf.api

import com.example.roleaf.model.WikiSummaryResponse
import com.example.roleaf.model.WikiMobileSectionsResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface WikipediaApi {
    // existing summary call (you already have something similar)
    @GET
    suspend fun getSummary(@Url url: String): Response<WikiSummaryResponse>

    // new: mobile-sections (returns structured sections incl. HTML text)
    @GET
    suspend fun getMobileSections(@Url url: String): Response<WikiMobileSectionsResponse>

    // NEW: fetch raw response bodies (for parse endpoint and defensive parsing)
    @GET
    suspend fun getRaw(@Url url: String): Response<ResponseBody>
}







