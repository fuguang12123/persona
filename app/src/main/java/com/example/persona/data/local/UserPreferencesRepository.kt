package com.example.persona.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val KEY_TOKEN = stringPreferencesKey("jwt_token")
    private val KEY_USER_ID = stringPreferencesKey("user_id")

    val authToken: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[KEY_TOKEN] }

    val userId: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[KEY_USER_ID] }

    suspend fun saveAuthData(token: String, userId: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TOKEN] = token
            preferences[KEY_USER_ID] = userId
        }
    }

    suspend fun clearAuth() {
        context.dataStore.edit { it.clear() }
    }
}
