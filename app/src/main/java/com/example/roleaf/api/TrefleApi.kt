// app/src/main/java/com/example/roleaf/api/TrefleApi.kt
package com.example.roleaf.api

import com.example.roleaf.model.TreflePlant
import com.example.roleaf.model.TrefleResponse
import com.example.roleaf.model.TrefleListResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TrefleApi {
    // Get by slug/id (returns single-data wrapper)
    @GET("api/v1/plants/{slug}")
    suspend fun getPlantBySlug(
        @Path("slug") slug: String,
        @Query("token") apiKey: String
    ): Response<TrefleResponse>

    // Search endpoint -> might return a list wrapper
    @GET("api/v1/plants/search")
    suspend fun searchPlant(
        @Query("q") query: String,
        @Query("token") apiKey: String
    ): Response<TrefleListResponse>
}




