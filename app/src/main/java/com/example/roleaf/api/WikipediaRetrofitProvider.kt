package com.example.roleaf.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Wikipedia Retrofit provider
 * - baseUrl is the root https://en.wikipedia.org/
 * - adds a User-Agent header required by Wikimedia's API & robot policy
 * - optional logging for debugging (remove or reduce in production)
 *
 * IMPORTANT: Replace the contact email below with your app or maintainer contact
 * string in the recommended UA format: "RoLeaf/1.0 (https://your.site/; your-email@example.com)"
 */
object WikipediaRetrofitProvider {

    private const val BASE = "https://en.wikipedia.org/" // trailing slash is important

    // Replace with your contact details so Wikimedia can reach you if necessary.
    // Format examples (pick one you prefer):
    // "RoLeaf/1.0 (https://roleaf.example.com/; dev@yourdomain.com)"
    // "RoLeafAndroid/1.0 (dev@yourdomain.com)"
    private const val USER_AGENT = "RoLeaf/1.0 (https://your-site.example/; dev@yourdomain.com)"

    private val userAgentInterceptor = Interceptor { chain ->
        val original: Request = chain.request()
        val request = original.newBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        chain.proceed(request)
    }

    // optional: http logging for debug (set level NONE in release)
    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttp = OkHttpClient.Builder()
        .addInterceptor(userAgentInterceptor)
        .addInterceptor(logging)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val api: WikipediaApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WikipediaApi::class.java)
    }
}

