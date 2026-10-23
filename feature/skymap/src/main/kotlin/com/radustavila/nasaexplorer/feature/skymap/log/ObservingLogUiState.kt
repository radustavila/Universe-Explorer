package com.radustavila.nasaexplorer.feature.skymap.log

import com.radustavila.nasaexplorer.core.model.DeepSkyCatalogue
import com.radustavila.nasaexplorer.core.model.DeepSkyObject
import com.radustavila.nasaexplorer.core.model.DeepSkyTonight
import com.radustavila.nasaexplorer.core.model.ObservingLogEntry

/** What tonight's sky allows, for the "Up tonight" filter and Sky tonight's card. */
sealed interface TonightSky {
    data object Loading : TonightSky

    /** No place saved: nothing can be worked out. */
    data object NoPlace : TonightSky

    /** The place's night never gets dark enough (summer at high latitudes). */
    data object NotDark : TonightSky

    data class Known(val tonight: DeepSkyTonight) : TonightSky
}

data class ObservingLogUiState(
    /** Every Messier and Caldwell object; null while the catalogue loads. */
    val objects: List<DeepSkyObject>? = null,
    /** IAU abbreviation to name ("And" to "Andromeda"). */
    val constellationNames: Map<String, String> = emptyMap(),
    val log: Map<String, ObservingLogEntry> = emptyMap(),
    val tonight: TonightSky = TonightSky.Loading,
    val catalogue: DeepSkyCatalogue = DeepSkyCatalogue.MESSIER,
    val notSeenOnly: Boolean = false,
    val upTonightOnly: Boolean = false,
) {
    /** [catalogue]'s objects by number. */
    val checklist: List<DeepSkyObject> by lazy { checklistOf(catalogue) }

    /** [checklist] with the filters applied. */
    val shown: List<DeepSkyObject> by lazy {
        val visible = (tonight as? TonightSky.Known)?.tonight?.visible
        checklist.filter { item ->
            (!notSeenOnly || item.id !in log) && (!upTonightOnly || visible?.containsKey(item.id) == true)
        }
    }

    fun checklistOf(catalogue: DeepSkyCatalogue): List<DeepSkyObject> =
        objects.orEmpty().filter { it.numberIn(catalogue) != null }.sortedBy { it.numberIn(catalogue) }

    fun seenCount(catalogue: DeepSkyCatalogue): Int = checklistOf(catalogue).count { it.id in log }

    /**
     * Sky tonight's "Tonight's best" (F23): Messier objects not in the log yet that climb 30° in the
     * dark hours, brightest first.
     */
    fun tonightsBest(limit: Int = TONIGHTS_BEST): List<DeepSkyObject> {
        val visible = (tonight as? TonightSky.Known)?.tonight?.visible ?: return emptyList()
        return checklistOf(DeepSkyCatalogue.MESSIER)
            .filter { it.id in visible && it.id !in log }
            .sortedBy { it.magnitude ?: Float.MAX_VALUE }
            .take(limit)
    }

    /** Messier objects up tonight, seen or not. */
    val messierUpTonight: Int
        get() {
            val visible = (tonight as? TonightSky.Known)?.tonight?.visible ?: return 0
            return checklistOf(DeepSkyCatalogue.MESSIER).count { it.id in visible }
        }

    companion object {
        const val TONIGHTS_BEST = 5
    }
}
