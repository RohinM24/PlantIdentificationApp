// File: app/src/main/java/com/example/roleaf/ui/viewmodel/MainViewModel.kt
package com.example.roleaf.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roleaf.data.IdentificationRepository
import com.example.roleaf.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ScreenState {
    object Splash : ScreenState()
    object Input : ScreenState()
    object Loading : ScreenState()
    object Result : ScreenState()
}

data class UiState(
    val screen: ScreenState = ScreenState.Splash,
    val organUris: Map<String, Uri?> = mapOf(
        "leaf" to null, "flower" to null, "fruit" to null, "bark" to null
    ),
    val plantNetResult: PlantNetResult? = null,
    val treflePlant: TreflePlant? = null,
    val wikiText: String? = null,         // lead / summary shown in Plant Description card
    val wikiExtra: String? = null,        // non-lead sections / wikiSections used by General Guide
    // Extras
    val careGuide: CareGuide? = null,
    val similarSpecies: List<SimilarSpeciesItem> = emptyList(),
    val morphology: MorphologyProfile? = null,
    val nativeRange: NativeRange? = null
)

class MainViewModel(private val repo: IdentificationRepository) : ViewModel() {

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    fun setScreen(s: ScreenState) {
        _ui.value = _ui.value.copy(screen = s)
    }

    fun setOrganUri(organ: String, uri: Uri?) {
        val updated = _ui.value.organUris.toMutableMap()
        updated[organ] = uri
        _ui.value = _ui.value.copy(organUris = updated)
    }

    fun clearOrgan(organ: String) {
        setOrganUri(organ, null)
    }

    fun identify() {
        Log.d("RoLeaf", "identify() called with organUris = ${_ui.value.organUris}")

        // Build ordered pairs from the organ->uri map preserving iteration order,
        // and only include entries that actually have a URI. This guarantees
        // the uriList and organsList are aligned.
        val presentPairs: List<Pair<String, Uri>> =
            _ui.value.organUris.entries
                .filter { it.value != null }
                .map { it.key to it.value!! }

        if (presentPairs.isEmpty()) {
            Log.w("RoLeaf", "No URIs to identify, aborting.")
            return
        }

        // Separate into parallel lists in the same order
        val organs: List<String> = presentPairs.map { it.first }
        val uris: List<Uri> = presentPairs.map { it.second }

        // Debug: log lengths & ordering to help troubleshooting
        Log.d("RoLeaf", "identify: sending ${uris.size} images for organs=$organs")

        _ui.value = _ui.value.copy(screen = ScreenState.Loading)

        viewModelScope.launch {
            try {
                val result = repo.identifyWithExtras(uris, organs)
                if (result == null) {
                    _ui.value = _ui.value.copy(screen = ScreenState.Input)
                    return@launch
                }

                _ui.value = _ui.value.copy(
                    plantNetResult = result.plantNetTop,
                    treflePlant = result.treflePlant,
                    wikiText = result.wikiExtract,      // lead/summary for Plant Description card
                    wikiExtra = result.wikiSections,    // full-page non-lead sections for General Guide
                    careGuide = result.careGuide,
                    similarSpecies = result.similarSpecies,
                    morphology = result.morphology,
                    nativeRange = result.nativeRange,
                    screen = ScreenState.Result
                )
                Log.d("RoLeaf", "wikiExtract length=${result.wikiExtract?.length ?: 0}, wikiSections length=${result.wikiSections?.length ?: 0}")

            } catch (e: Exception) {
                e.printStackTrace()
                _ui.value = _ui.value.copy(screen = ScreenState.Input)
            }
        }
    }



    fun retryTrefleLookupWithName(name: String) {
        viewModelScope.launch {
            try {
                val trefle = repo.fetchTrefleByName(name, null)
                _ui.value = _ui.value.copy(treflePlant = trefle)
            } catch (e: Exception) {
                Log.w("RoLeaf", "retryTrefleLookupWithName failed: ${e.message}")
            }
        }
    }

    fun resetToInput() {
        // reset fully to initial UiState (clears wikiExtra as requested)
        _ui.value = UiState(screen = ScreenState.Input)
    }
}



