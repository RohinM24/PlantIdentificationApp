RoLeaf

Plant Identification Android Application (Kotlin + Jetpack Compose + Retrofit)

RoLeaf is an Android application that allows users to identify plants by taking or uploading photos. The app integrates the PlantNet API for recognition and enriches results using Trefle and Wikipedia, providing comprehensive species information in a modern, clean UI.

Features
Plant Identification

Take a photo or upload from gallery

Multiple organ selection (leaf, flower, fruit, bark, etc.)

Uploads image(s) to the PlantNet API

Displays top species matches with confidence scores

Plant Information Retrieval

Automatically fetches plant details from Trefle (taxonomy, common names, images, growth info, etc.)

Wikipedia integration for summaries and additional context

Defensive fallback logic for missing fields across APIs

Automatic slug sanitization for Trefle API compatibility

User Interface

Jetpack Compose UI

Image preview before submission

Result cards for PlantNet matches, Trefle data, and Wikipedia summaries

Scrollable and responsive layout

UI theme aligned with RoLeaf branding

Ability to remove mistakenly added images without breaking layout

Reliability Enhancements

OkHttp interceptor for safe JSON logging without consuming response bodies

Automatic fallback to Trefle search endpoint when direct slug lookup fails

Graceful handling of missing fields and partial results

Debounced UI updates to prevent flicker during loading

Tech Stack
Languages & Frameworks

Kotlin

Jetpack Compose

AndroidX ViewModel & Lifecycle

Networking

Retrofit

Gson

OkHttp + Logging Interceptor

Media & UI

Glide for image loading

Activity Result APIs for camera and gallery

APIs Used

PlantNet API – Plant identification using image recognition

Trefle API – Botanical data (taxonomy, images, descriptions)

Wikipedia API – Summary descriptions for species

Project Structure
app/
 ├─ api/
 │   ├─ PlantNetApi.kt
 │   ├─ TrefleApi.kt
 │   └─ WikipediaApi.kt
 ├─ data/
 │   └─ IdentificationRepository.kt
 ├─ model/
 │   ├─ PlantNet models
 │   ├─ Trefle models
 │   └─ WikiSummaryResponse.kt
 ├─ ui/
 │   ├─ screens/
 │   │   ├─ InitialScreen.kt
 │   │   └─ ResultScreen.kt
 │   └─ components/
 │       └─ ResultCards.kt
 ├─ util/
 │   └─ ImageUtils.kt
 ├─ MainActivity.kt
 ├─ Manifest.xml
 └─ resources/
     ├─ layout XML (for legacy views)
     ├─ themes
     └─ strings.xml

Setup Instructions
1. Clone the Repository
git clone https://github.com/yourusername/RoLeaf.git
cd RoLeaf

2. Add API Keys

Create a local.properties file (if one does not already exist):

PLANTNET_API_KEY=your_key_here
TREFLE_API_KEY=your_key_here


The app’s build.gradle automatically injects these values.

3. Build and Run

Open in Android Studio, sync Gradle, and run on an emulator or physical device.

How It Works

User selects one or more plant organs

Image(s) are sent to PlantNet

Top species results are displayed with confidence scores

RoLeaf retrieves enhanced species data from Trefle

If slug lookup fails, search endpoint is used

Slugs are sanitized automatically

A Wikipedia summary is fetched and displayed

All results are shown in structured cards

Recent Improvements (Memory of Project Development)

Added safe slug sanitization to avoid Trefle lookup failures

Implemented automatic search fallback when exact match fails

Added OkHttp JSON logging interceptor

Dynamic Trefle result display (fields shown only if present)

Improved image loading and fallback logic for missing Trefle images

Updated UI theming to match the RoLeaf results screen

Added functionality for users to remove mistakenly added images

Roadmap

Offline caching of recent identifications

Search history

Light/Dark theme dynamic switching

Auto-crop and background removal for plant photos

License

This project is open-source and available under the MIT License.
