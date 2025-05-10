import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FilterState(
    val title: String = "",
    val genres: List<String> = emptyList(),
    val years: List<String> = emptyList(),
    val ratingMin: Int = 0,
    val ratingMax: Int = 5,
    val sortMethod: String = "None",
    val label: List<String> = emptyList(),
    val festival: List<String> = emptyList(),
) : Parcelable