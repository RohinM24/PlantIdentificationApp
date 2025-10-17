package com.example.roleaf

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.gson.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.*
import retrofit2.Response
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.File
import java.lang.reflect.Type

// ------------------ Safe JSON Adapter ------------------

class SafeStringAdapter : JsonDeserializer<String?> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): String? {
        return when {
            json.isJsonNull -> null
            json.isJsonPrimitive && json.asJsonPrimitive.isString -> json.asString
            json.isJsonObject -> json.asJsonObject.toString()
            else -> json.toString()
        }
    }
}

// ------------------ Data Models ------------------

data class PlantNetResponse(val results: List<PlantNetResult>?)
data class PlantNetResult(val species: Species?, val score: Double?)
data class Species(
    val scientificName: String?,
    val scientificNameWithoutAuthor: String?,
    val commonNames: List<String>?,
    val family: Family?,
    val genus: Genus?
)
data class Family(val scientificName: String?)
data class Genus(val scientificName: String?)

data class TrefleResponse(val data: TreflePlant?)
data class TrefleSearchResponse(val data: List<TreflePlant>?)
data class TreflePlant(
    val id: Int?,
    val slug: String?,
    val common_name: String?,
    val scientific_name: String?,
    val family_common_name: String?,
    val image_url: String?,
    val bibliography: String?,
    val main_species: MainSpecies?,
    val distributions: Map<String, List<Distribution>>?
)
data class MainSpecies(
    val specifications: Specifications?,
    val growth: Growth?,
    val foliage: Foliage?,
    val flower: Flower?,
    val fruit: Fruit?,
    val distribution: Distribution?
)
data class Specifications(val growth_form: String?, val average_height: Height?)
data class Height(val cm: Double?, val m: Double?)
data class Growth(
    val description: String?,
    val light: Int?,
    val soil_texture: Int?,
    val minimum_temperature: Map<String, Any>?,
    val atmospheric_humidity: Int?
)
data class Foliage(val texture: String?, val color: String?)
data class Flower(val color: List<String>?, val conspicuous: Boolean?)
data class Fruit(val color: List<String>?, val conspicuous: Boolean?)
data class Distribution(val native: List<String>?, val introduced: List<String>?)
data class WikipediaResponse(val extract: String?)

// ------------------ Retrofit Interfaces ------------------

interface PlantNetApi {
    @Multipart
    @POST("v2/identify/all")
    suspend fun identifyPlant(
        @Part image: MultipartBody.Part,
        @Part("organs") organs: RequestBody,
        @Query("api-key") apiKey: String
    ): PlantNetResponse
}

interface TrefleApi {
    @GET("api/v1/species/{slug}")
    suspend fun getPlantBySlug(
        @Path("slug") slug: String,
        @Query("token") token: String,
        @Query("complete_data") complete: Boolean = true
    ): Response<TrefleResponse>

    @GET("api/v1/plants/{id}")
    suspend fun getPlantById(@Path("id") id: Int, @Query("token") token: String): Response<TrefleResponse>

    @GET("api/v1/species/search")
    suspend fun searchSpecies(@Query("token") token: String, @Query("q") query: String): TrefleSearchResponse

    @GET("api/v1/plants/search")
    suspend fun searchPlant(@Query("token") token: String, @Query("q") query: String): TrefleSearchResponse
}

interface WikipediaApi {
    @GET("page/summary/{title}")
    suspend fun getSummary(@Path("title") title: String): WikipediaResponse
}

// ------------------ Main Activity ------------------

class MainActivity : AppCompatActivity() {

    private lateinit var previewImage: ImageView
    private lateinit var btnSelect: Button
    private lateinit var btnIdentify: Button
    private lateinit var organSpinner: Spinner
    private lateinit var progress: ProgressBar
    private lateinit var confidenceBar: ProgressBar
    private lateinit var resultText: TextView
    private lateinit var trefleText: TextView
    private lateinit var wikiText: TextView

    private var imageUri: Uri? = null
    private var photoUri: Uri? = null

    private val plantNetApiKey = BuildConfig.PLANTNET_API_KEY
    private val trefleApiKey = BuildConfig.TREFLE_API_KEY

    private val gson = GsonBuilder()
        .registerTypeAdapter(String::class.java, SafeStringAdapter())
        .create()

    private val retrofitPlantNet = Retrofit.Builder()
        .baseUrl("https://my-api.plantnet.org/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val retrofitTrefle = Retrofit.Builder()
        .baseUrl("https://trefle.io/")
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    private val wikiClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val newReq = chain.request().newBuilder()
                .header("User-Agent", "RoLeafApp/1.0 (https://roleaf.example; contact@roleaf.com)")
                .build()
            chain.proceed(newReq)
        }
        .build()

    private val retrofitWiki = Retrofit.Builder()
        .baseUrl("https://en.wikipedia.org/api/rest_v1/")
        .client(wikiClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val plantNetService = retrofitPlantNet.create(PlantNetApi::class.java)
    private val trefleService = retrofitTrefle.create(TrefleApi::class.java)
    private val wikiService = retrofitWiki.create(WikipediaApi::class.java)

    // ------------------ ActivityResult Launchers ------------------

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                imageUri = it
                Glide.with(this).load(it).into(previewImage)
                btnIdentify.isEnabled = true
            }
        }

    private val takePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && photoUri != null) {
                imageUri = photoUri
                Glide.with(this).load(photoUri).into(previewImage)
                btnIdentify.isEnabled = true
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewImage = findViewById(R.id.previewImage)
        btnSelect = findViewById(R.id.btnSelect)
        btnIdentify = findViewById(R.id.btnIdentify)
        organSpinner = findViewById(R.id.organSpinner)
        progress = findViewById(R.id.progress)
        confidenceBar = findViewById(R.id.confidenceBar)
        resultText = findViewById(R.id.resultText)
        trefleText = findViewById(R.id.trefleText)
        wikiText = findViewById(R.id.wikiText)

        btnSelect.setOnClickListener { showImageSourceDialog() }
        btnIdentify.setOnClickListener { identifyPlant() }

        checkPermissions()
    }

    // ------------------ Image Selection Dialog ------------------

    private fun showImageSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        AlertDialog.Builder(this)
            .setTitle("Select Image Source")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchCamera(takePhotoLauncher)
                    1 -> pickImageLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun launchCamera(takePhotoLauncher: ActivityResultLauncher<Uri>) {
        try {
            val imageFile = File.createTempFile("plant_photo_", ".jpg", cacheDir)
            photoUri = FileProvider.getUriForFile(this, "${packageName}.provider", imageFile)
            takePhotoLauncher.launch(photoUri)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open camera: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("CAMERA", "Error launching camera", e)
        }
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
        )
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty())
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 0)
    }

    // ------------------ Plant Identification Logic ------------------

    private fun identifyPlant() {
        imageUri?.let { uri ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    withContext(Dispatchers.Main) {
                        progress.visibility = View.VISIBLE
                        resultText.text = "🔍 Identifying..."
                        trefleText.text = ""
                        wikiText.text = ""
                        confidenceBar.progress = 0
                    }

                    val file = File(cacheDir, "upload.jpg")
                    contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }

                    val organs = organSpinner.selectedItem?.toString()?.lowercase() ?: "leaf"
                    val requestBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    val imagePart = MultipartBody.Part.createFormData("images", file.name, requestBody)
                    val organsPart = RequestBody.create("text/plain".toMediaTypeOrNull(), organs)

                    val response = plantNetService.identifyPlant(imagePart, organsPart, plantNetApiKey)
                    val top = response.results?.firstOrNull()

                    if (top != null) {
                        val sci = top.species?.scientificNameWithoutAuthor ?: top.species?.scientificName ?: "Unknown"
                        val common = top.species?.commonNames?.joinToString(", ") ?: "Unknown"
                        val family = top.species?.family?.scientificName ?: "Unknown family"
                        val genus = top.species?.genus?.scientificName ?: "Unknown genus"
                        val confidence = ((top.score ?: 0.0) * 100).toInt()

                        withContext(Dispatchers.Main) {
                            confidenceBar.progress = confidence
                            resultText.text = """
                                🌿 Top guess: $common
                                🧬 Scientific: $sci
                                🏡 Family: $family
                                🌱 Genus: $genus
                                📊 Confidence: $confidence%
                            """.trimIndent()
                        }

                        val treflePlant = fetchTrefleData(sci)
                        val wikiInfo = fetchWikipedia(sci)

                        withContext(Dispatchers.Main) {
                            displayTrefle(treflePlant)
                            wikiText.text = wikiInfo ?: "📖 No info found."
                        }
                    } else {
                        withContext(Dispatchers.Main) { resultText.text = "❌ No plant identified." }
                    }

                } catch (e: Exception) {
                    Log.e("PLANTNET", "Error: ${e.message}", e)
                    withContext(Dispatchers.Main) { resultText.text = "Error: ${e.message}" }
                } finally {
                    withContext(Dispatchers.Main) { progress.visibility = View.GONE }
                }
            }
        } ?: Toast.makeText(this, "Select an image first", Toast.LENGTH_SHORT).show()
    }

    // ------------------ Trefle & Wikipedia ------------------

    private suspend fun fetchTrefleData(sci: String): TreflePlant? {
        return try {
            val cleanSci = sci.replace(Regex("\\s*\\(.*\\)"), "")
                .split(" ")
                .take(2)
                .joinToString(" ")
                .trim()
            val slug = cleanSci.lowercase().replace(" ", "-")
            Log.d("TREFLE", "🔎 Trying slug: $slug")

            val slugResponse = trefleService.getPlantBySlug(slug, trefleApiKey, complete = true)
            val slugData = slugResponse.body()?.data
            if (slugData?.main_species != null &&
                (slugData.main_species.growth != null ||
                        slugData.main_species.foliage != null ||
                        slugData.main_species.flower != null)) {
                return slugData
            }

            val speciesSearch = trefleService.searchSpecies(trefleApiKey, cleanSci)
            val match = speciesSearch.data?.firstOrNull()
            if (match != null && match.id != null) {
                val detail = trefleService.getPlantById(match.id, trefleApiKey)
                val plant = detail.body()?.data
                if (plant != null && plant.main_species != null) return plant
            }

            val genus = cleanSci.split(" ").firstOrNull() ?: return null
            val searchResponse = trefleService.searchPlant(trefleApiKey, genus)
            searchResponse.data?.firstOrNull()
        } catch (e: Exception) {
            Log.e("TREFLE", "Error fetching Trefle: ${e.message}")
            null
        }
    }

    private fun displayTrefle(plant: TreflePlant?) {
        if (plant == null) {
            trefleText.text = "🌱 No data available."
            return
        }

        val sb = StringBuilder()
        sb.appendLine("🌿 ${plant.common_name ?: "Unknown"}")
        sb.appendLine("🧬 ${plant.scientific_name ?: "N/A"}")

        val main = plant.main_species
        main?.specifications?.let {
            sb.appendLine("\n📏 Specifications:")
            sb.appendLine("• Growth form: ${it.growth_form ?: "N/A"}")
            val height = it.average_height?.m ?: it.average_height?.cm?.div(100)
            if (height != null) sb.appendLine("• Avg height: ${height} m")
        }

        main?.growth?.let {
            sb.appendLine("\n🌱 Growth:")
            sb.appendLine("• Light: ${it.light ?: "N/A"} / 10")
            sb.appendLine("• Soil Texture: ${it.soil_texture ?: "N/A"} / 10")
            val temp = when (val deg = it.minimum_temperature?.get("deg_c")) {
                is Number -> deg.toString()
                is String -> deg
                else -> "N/A"
            }
            sb.appendLine("• Min Temp: $temp °C")
            sb.appendLine("• Humidity: ${it.atmospheric_humidity ?: "N/A"} %")

            if (!it.description.isNullOrBlank())
                sb.appendLine("• Description: ${it.description}")
        }

        main?.foliage?.let {
            sb.appendLine("\n🍃 Foliage: ${it.color ?: "N/A"} (${it.texture ?: "N/A"})")
        }

        main?.flower?.let {
            sb.appendLine("\n🌸 Flower: ${it.color?.joinToString(", ") ?: "N/A"}")
        }

        main?.fruit?.let {
            sb.appendLine("\n🍒 Fruit: ${it.color?.joinToString(", ") ?: "N/A"}")
        }

        plant.distributions?.get("native")?.let { nativeList ->
            if (nativeList.isNotEmpty()) sb.appendLine("\n🌍 Native to: ${nativeList.joinToString(", ")}")
        }

        plant.distributions?.get("introduced")?.let { introducedList ->
            if (introducedList.isNotEmpty()) sb.appendLine("\n🪴 Introduced in: ${introducedList.joinToString(", ")}")
        }

        plant.bibliography?.let { sb.appendLine("\n📚 References: $it") }

        trefleText.text = sb.toString()
        plant.image_url?.let { Glide.with(this).load(it).into(previewImage) }
    }

    private suspend fun fetchWikipedia(sci: String): String? {
        return try {
            val clean = sci.replace(Regex("\\s*\\(.*\\)"), "").trim()
            val title = clean.replace(" ", "_").replace(Regex("[^A-Za-z_]"), "")
            var response = wikiService.getSummary(title)

            if (response.extract == null) {
                val parts = clean.split(" ")
                if (parts.size > 1) {
                    val shortTitle = parts.take(2).joinToString("_")
                    Log.d("WIKI", "Retrying with: $shortTitle")
                    response = wikiService.getSummary(shortTitle)
                }
            }

            response.extract
        } catch (e: Exception) {
            Log.e("WIKI", "Error: ${e.message}")
            null
        }
    }
}
