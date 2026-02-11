import android.content.Context
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.navigation.NavDirections
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.api.subsonic.models.Filter
import org.moire.ultrasonic.api.subsonic.models.Filters
import org.moire.ultrasonic.util.Settings.maxSongs

object TileStorage {
    private const val KEY = "user_tiles"

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

val tileInfoColors = listOf(
    "#000000", "#00008B", "#8B0000", "#006400", "#4B0082",
    "#808080", "#2F4F4F", "#A9A9A9", "#800000", "#2C3E50",
    "#8B4513", "#3B3B3B", "#556B2F", "#2F4F4F", "#D2691E",
    "#B22222", "#4C4C4C", "#3A3A3A", "#4E4E4E", "#6A5ACD"
).map { it.toColorInt() }

val genreIconMap: Map<String, Int> = mapOf(
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
    val genre: List<String>? = null,
    val year: List<String>? = null,
    val size: Int = maxSongs,
    val festival: List<String>? = null,
    val festivalLineup: String? = null,
    val label: List<String>? = null,
    val offset: Int = 0,
    val length: String = "short",
    val ratingMin: Int = 0,
    val ratingMax: Int = 5,
    val sortMethod: String = "AddedDesc",
    val favorite: Boolean = false
) {
    init {
        if (title == "") title = createDefaultTitle()
    }

    private fun createDefaultTitle(): String = buildString {
        when (sortMethod) {
            "AddedDesc" -> append("Recent ")
            "DateDescAndRelease" -> append("Recent ")
            "Random" -> append("Random ")
            "LastWrittenDesc" -> append("Recent Modified ")
        }

        fun pick(value: List<String>?): String? = value?.filter { it.isNotBlank() && it != "All" }
            ?.takeIf { it.isNotEmpty() }
            ?.let { if (it.size == 1) it.first() else "Multiple" }

        listOfNotNull(pick(festival), pick(label), pick(genre))
            .joinToString(" ")
            .let { if (it.isNotBlank()) append(it) }

        pick(year)?.let { append(" ($it)") }
    }
}

fun navigateToGenre(tile: TileInfo): NavDirections {
    val filters = Filters()

    tile.genre
        ?.filter { it.isNotBlank() && it != "All" }
        ?.takeIf { it.isNotEmpty() }
        ?.let {
            filters.add(if (it.size == 1) Filter("GENRE", it[0]) else Filter("GENRE", it))
        }

    tile.year
        ?.filter { it.isNotBlank() && it != "All" }
        ?.takeIf { it.isNotEmpty() }
        ?.let {
            filters.add(if (it.size == 1) Filter("YEAR", it[0]) else Filter("YEAR", it))
        }

    tile.label
        ?.filter { it.isNotBlank() && it != "All" }
        ?.takeIf { it.isNotEmpty() }
        ?.let {
            filters.add(if (it.size == 1) Filter("PUBLISHER", it[0]) else Filter("PUBLISHER", it))
        }

    tile.festival
        ?.filter { it.isNotBlank() && it != "All" }
        ?.takeIf { it.isNotEmpty() }
        ?.let {
            filters.add(if (it.size == 1) Filter("FESTIVAL", it[0]) else Filter("FESTIVAL", it))
        }

    filters.add(Filter("LENGTH", tile.length))

    val filtersJson = Gson().toJson(filters)

    return NavigationGraphDirections.toTrackCollection(
        songs = tile.title,
        filters = filtersJson,
        size = tile.size,
        offset = tile.offset,
        length = tile.length,
        ratingMin = tile.ratingMin,
        ratingMax = tile.ratingMax,
        sortMethod = tile.sortMethod,
        festivalLineup = tile.festivalLineup
    )
}
