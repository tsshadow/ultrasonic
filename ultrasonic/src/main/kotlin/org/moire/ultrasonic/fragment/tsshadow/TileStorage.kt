import android.content.Context
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import androidx.core.content.edit

object TileStorage {
    private val KEY = "user_tiles"

    fun saveTiles(context: Context, tiles: MutableList<TileInfo>, page: String) {
        val prefs = context.getSharedPreferences(page+"_tiles", Context.MODE_PRIVATE)
        val json = Gson().toJson(tiles)
        prefs.edit() { putString(KEY, json) }
    }

    fun loadTiles(context: Context, page: String): MutableList<TileInfo> {
        val prefs = context.getSharedPreferences(page+"_tiles", Context.MODE_PRIVATE)
        val json = prefs.getString(KEY, "[]")
        return Gson().fromJson(json, object : TypeToken<MutableList<TileInfo>>() {}.type)
    }
}