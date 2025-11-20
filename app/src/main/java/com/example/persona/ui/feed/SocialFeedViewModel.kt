package com.example.persona.ui.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.persona.data.model.Persona
import com.example.persona.data.remote.PersonaService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SocialFeedViewModel @Inject constructor(
    private val personaService: PersonaService
) : ViewModel() {

    var feedList by mutableStateOf<List<Persona>>(emptyList())
    var isLoading by mutableStateOf(false)

    init {
        loadFeed()
    }

    fun loadFeed() {
        viewModelScope.launch {
            isLoading = true
            try {
                val response = personaService.getFeed()
                if (response.isSuccessful && response.body()?.code == 200) {
                    feedList = response.body()?.data ?: emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }
}
