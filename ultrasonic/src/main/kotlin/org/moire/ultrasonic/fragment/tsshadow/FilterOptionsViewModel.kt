package org.moire.ultrasonic.fragment.tsshadow

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class FilterOptionsViewModel : ViewModel() {
    val genres = MutableLiveData<List<String>>()
    val artists = MutableLiveData<List<String>>()
    val years = MutableLiveData<List<String>>()
    val labels = MutableLiveData<List<String>>()
    val festivals = MutableLiveData<List<String>>()
    val lineups = MutableLiveData<List<String>>()
    val favorite = MutableLiveData<Boolean>()
}
