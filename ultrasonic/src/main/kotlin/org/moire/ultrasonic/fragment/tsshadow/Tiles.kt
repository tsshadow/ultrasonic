import TileStorage.saveTiles
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.graphics.toColorInt
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import org.moire.ultrasonic.NavigationGraphDirections
import org.moire.ultrasonic.R
import org.moire.ultrasonic.util.Settings.maxSongs
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import androidx.core.content.edit
import timber.log.Timber

object TileStorage {
    private val KEY = "user_tiles"

    fun saveTiles(context: Context, tiles: MutableList<TileInfo>, page: String) {
        val prefs = context.getSharedPreferences(page + "_tiles", Context.MODE_PRIVATE)
        val json = Gson().toJson(tiles)
        prefs.edit() { putString(KEY, json) }
    }

    fun loadTiles(context: Context, page: String): MutableList<TileInfo> {
        val prefs = context.getSharedPreferences(page + "_tiles", Context.MODE_PRIVATE)
        val json = prefs.getString(KEY, "[]")
        return Gson().fromJson(json, object : TypeToken<MutableList<TileInfo>>() {}.type)
    }
}

/**
 * Defines the default gradient color pool used for tiles.
 */
private val tileInfoColors = listOf(
    "#000000", "#00008B", "#8B0000", "#006400", "#4B0082",
    "#808080", "#2F4F4F", "#A9A9A9", "#800000", "#2C3E50",
    "#8B4513", "#3B3B3B", "#556B2F", "#2F4F4F", "#D2691E",
    "#B22222", "#4C4C4C", "#3A3A3A", "#4E4E4E", "#6A5ACD"
).map { it.toColorInt() }

/**
 * Icon map to visually distinguish different genres.
 */
private val genreIconMap: Map<String, Int> = mapOf(
    // General presets
    "Recent Songs" to R.drawable.baseline_music_note_24,
    "Random Songs" to R.drawable.baseline_music_note_24,
    "Recent Livesets" to R.drawable.baseline_music_note_24,
    "Random Livesets" to R.drawable.baseline_music_note_24,

    // Softer genres
    "Euphoric Hardstyle" to R.drawable.baseline_emoji_emotions_24,
    "Hardstyle" to R.drawable.baseline_mood_24,
    "Mainstream Hardstyle" to R.drawable.baseline_mood_24,
    "Hardstyle Classics" to R.drawable.baseline_headset_24,

    // Harder genres
    "Raw Hardstyle" to R.drawable.baseline_local_fire_department_24,
    "Mainstream Hardcore" to R.drawable.baseline_thunderstorm_24,
    "Hardcore" to R.drawable.baseline_bolt_24,
    "Millennium Hardcore" to R.drawable.baseline_headset_24,
    "Industrial Hardcore" to R.drawable.baseline_local_fire_department_24,
    "Uptempo Hardcore" to R.drawable.baseline_thunderstorm_24,
    "Bouncy Uptempo" to R.drawable.baseline_emoji_emotions_24,
    "Zaagtempo" to R.drawable.baseline_bolt_24
)

/**
 * Describes the configuration for a tile that represents a genre or song preset.
 */
data class TileInfo(
    var title: String,
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
)

/**
 * Returns a navigation action for a given tile.
 */
fun navigateToGenre(tile: TileInfo): NavDirections {
    Timber.d(
        "Navigating to track collection for: '${tile.title}' with parameters: " +
                "genre=${tile.genre}, " +
                "festival=${tile.festival}, " +
                "label=${tile.label}, " +
                "year=${tile.year}, " +
                "size=${tile.size}, " +
                "offset=${tile.offset}, " +
                "length=${tile.length}, " +
                "ratingMin=${tile.ratingMin}, " +
                "ratingMax=${tile.ratingMax}, " +
                "sortMethod=${tile.sortMethod}"
    )
    return NavigationGraphDirections.toTrackCollection(
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
}


/**
 * Creates a styled tile view for the given [TileInfo] and adds navigation on click.
 */
fun createTileView(
    tile: TileInfo,
    index: Int,
    context: Context,
    gridLayout: GridLayout,
    navigationController: NavController,
    tileList: MutableList<TileInfo>,
    page: String
): View {
    val inflater = LayoutInflater.from(context)
    val tileView = inflater.inflate(R.layout.tsshadow_tile_layout, gridLayout, false)

    val tileCard = tileView.findViewById<View>(R.id.tile_card)
    val tileIcon = tileView.findViewById<ImageView>(R.id.tile_icon)
    val tileText = tileView.findViewById<TextView>(R.id.tile_text)

    // Gradient background
    val startColor = tileInfoColors[index % tileInfoColors.size]
    val endColor = Color.BLACK
    val gradient = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(startColor, endColor)
    ).apply {
        cornerRadius = 24f
    }

    tileCard.background = gradient

    // Set genre-based icon, fallback to default
    @DrawableRes val iconRes = genreIconMap[tile.genre] ?: R.drawable.baseline_music_note_24
    tileIcon.setImageResource(iconRes)
    tileIcon.contentDescription = tile.genre ?: tile.title
    tileText.text = tile.title

    // Click handler
    tileView.setOnClickListener {
        navigationController.navigate(navigateToGenre(tile))
    }

    tileView.setOnLongClickListener {
        AlertDialog.Builder(context)
            .setTitle("Tile verwijderen?")
            .setMessage("Weet je zeker dat je '${tile.title}' wilt verwijderen?")
            .setPositiveButton("Ja") { _, _ ->
                gridLayout.removeView(tileView)
                tileList.remove(tile)
                saveTiles(context, tileList, page)
            }
            .setNegativeButton("Annuleren", null)
            .show()
        true
    }

    return tileView
}

