package com.example.roleaf.api

import com.example.roleaf.model.PlantNetResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * Updated PlantNet API:
 * Sends the API key as a query parameter instead of a multipart part.
 */
interface PlantNetApi {
    @Multipart
    @POST("v2/identify/all")
    suspend fun identifyPlant(
        @Part images: List<MultipartBody.Part>,
        @Part("organs") organs: RequestBody,
        @Query("api-key") apiKey: String       // ✅ API key must be a query parameter
    ): Response<PlantNetResponse>
}







