import androidx.lifecycle.ViewModel
import androidx.lifecycle.MutableLiveData

class FilterOptionsViewModel : ViewModel() {
    val genres = MutableLiveData<List<String>>()
    val years = MutableLiveData<List<String>>()
    val labels = MutableLiveData<List<String>>()
    val festivals = MutableLiveData<List<String>>()
}
