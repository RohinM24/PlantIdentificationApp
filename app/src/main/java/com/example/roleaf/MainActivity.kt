// File: app/src/main/java/com/example/roleaf/MainActivity.kt
package com.example.roleaf

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roleaf.api.PlantNetApi
import com.example.roleaf.api.TrefleApi
import com.example.roleaf.api.WikipediaApi
import com.example.roleaf.data.IdentificationRepository
import com.example.roleaf.ui.screens.AppNavHost
import com.example.roleaf.ui.theme.RoleafTheme
import com.example.roleaf.ui.viewmodel.MainViewModel
import com.example.roleaf.ui.viewmodel.MainViewModelFactory
import com.example.roleaf.util.SafeStringAdapter
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : ComponentActivity() {

    private val gson = GsonBuilder()
        .registerTypeAdapter(String::class.java, SafeStringAdapter())
        .create()

    private val client by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    private val retrofitPlantNet: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://my-api.plantnet.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    private val retrofitTrefle: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://trefle.io/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    companion object {
        private const val USER_AGENT = "RoLeaf/1.0 (https://your-site.example/; dev@yourdomain.com)"
    }

    private val wikiClient by lazy {
        val logging = HttpLoggingInterceptor().apply { level = Level.BASIC }
        val userAgentInterceptor = Interceptor { chain ->
            val original: Request = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .method(original.method, original.body)
                .build()
            chain.proceed(request)
        }
        OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(logging)
            .build()
    }

    // Use english wiki by default; for multi-language you can create other retrofit instances
    private val retrofitWikiEn: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://en.wikipedia.org/")
            .client(wikiClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    private val plantNetService: PlantNetApi by lazy { retrofitPlantNet.create(PlantNetApi::class.java) }
    private val trefleService: TrefleApi by lazy { retrofitTrefle.create(TrefleApi::class.java) }
    private val wikiService: WikipediaApi by lazy { retrofitWikiEn.create(WikipediaApi::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("RoLeaf", "🔑 Loaded PlantNet key from BuildConfig: ${BuildConfig.PLANTNET_API_KEY.take(10)}...")
        Log.d("RoLeaf", "✅ PlantNet key length: ${BuildConfig.PLANTNET_API_KEY.length}")
        Log.d("RoLeaf", "✅ Trefle key length: ${BuildConfig.TREFLE_API_KEY.length}")

        val repository = IdentificationRepository(
            context = applicationContext,
            plantNetService = plantNetService,
            trefleService = trefleService,
            wikiService = wikiService,
            plantNetKey = BuildConfig.PLANTNET_API_KEY,
            trefleKey = BuildConfig.TREFLE_API_KEY
        )

        val factory = MainViewModelFactory(repository)

        setContent {
            RoleafTheme {
                val vm: MainViewModel = viewModel(factory = factory)
                AppNavHost(viewModel = vm)
            }
        }
    }

    fun appContext(): Context = applicationContext
}


