/*
 * TileInfo.kt
 * Copyright (C) 2009-2025 Teun.Schriks
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

import android.content.Context
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.util.Settings.maxSongs
import com.google.common.reflect.TypeToken
import com.google.gson.Gson

object TileStorage {
    private val KEY = "user_tiles"

    fun saveTiles(context: Context, tiles: MutableList<TileInfo>, page: String) {
        val prefs = context.getSharedPreferences("${page}_tiles", Context.MODE_PRIVATE)
        val json = Gson().toJson(tiles)
        prefs.edit { putString(KEY, json) }
    }

    fun loadTiles(context: Context, page: String): MutableList<TileInfo> {
        val prefs = context.getSharedPreferences("${page}_tiles", Context.MODE_PRIVATE)
        val json = prefs.getString(KEY, "[]")
        return Gson().fromJson(json, object : TypeToken<MutableList<TileInfo>>() {}.type)
    }
}

public val tileInfoColors = listOf(
    "#000000", "#00008B", "#8B0000", "#006400", "#4B0082",
    "#808080", "#2F4F4F", "#A9A9A9", "#800000", "#2C3E50",
    "#8B4513", "#3B3B3B", "#556B2F", "#2F4F4F", "#D2691E",
    "#B22222", "#4C4C4C", "#3A3A3A", "#4E4E4E", "#6A5ACD"
).map { it.toColorInt() }

public val genreIconMap: Map<String, Int> = mapOf(
    "Recent Songs" to R.drawable.baseline_music_note_24,
    "Random Songs" to R.drawable.baseline_music_note_24,
    "Recent Livesets" to R.drawable.baseline_music_note_24,
    "Random Livesets" to R.drawable.baseline_music_note_24,
    "Euphoric Hardstyle" to R.drawable.baseline_emoji_emotions_24,
    "Hardstyle" to R.drawable.baseline_mood_24,
    "Mainstream Hardstyle" to R.drawable.baseline_mood_24,
    "Hardstyle Classics" to R.drawable.baseline_headset_24,
    "Raw Hardstyle" to R.drawable.baseline_local_fire_department_24,
    "Mainstream Hardcore" to R.drawable.baseline_thunderstorm_24,
    "Hardcore" to R.drawable.baseline_bolt_24,
    "Millennium Hardcore" to R.drawable.baseline_headset_24,
    "Industrial Hardcore" to R.drawable.baseline_local_fire_department_24,
    "Uptempo Hardcore" to R.drawable.baseline_thunderstorm_24,
    "Bouncy Uptempo" to R.drawable.baseline_emoji_emotions_24,
    "Zaagtempo" to R.drawable.baseline_bolt_24
)

class TileInfo(
    var title: String = "",
    val genre: String? = null,
    val year: String? = null,
    val size: Int = maxSongs,
    val festival: String? = null,
    val label: String? = null,
    val offset: Int = 0,
    val length: String = "short",
    val ratingMin: Int = 0,
    val ratingMax: Int = 5,
    val sortMethod: String = "AddedDesc"
) {
    init {
        if (title == "") title = createDefaultTitle()
    }

    private fun createDefaultTitle(): String {
        return buildString {
            when (sortMethod) {
                "AddedDesc" -> append("Recent ")
                "Random" -> append("Random ")
                "LastWrittenDesc" -> append("Recent Modified ")
            }
            listOf(festival, label, genre)
                .filter { !it.isNullOrBlank() && it != "All" }
                .joinToString(" ")
                .let { if (it.isNotBlank()) append(it) }
            if (!year.isNullOrBlank() && year != "All") {
                append(" ($year)")
            }
        }
    }
}

fun navigateToGenre(tile: TileInfo) = NavigationGraphDirections.toTrackCollection(
    songs = tile.title,
    genre = tile.genre,
    size = tile.size,
    offset = tile.offset,
    year = tile.year,
    festival = tile.festival,
    label = tile.label,
    length = tile.length,
    ratingMin = tile.ratingMin,
    ratingMax = tile.ratingMax,
    sortMethod = tile.sortMethod
)
