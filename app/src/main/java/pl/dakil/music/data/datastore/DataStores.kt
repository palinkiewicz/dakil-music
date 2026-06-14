package pl.dakil.music.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

// Two separate stores keep favorites churn from rewriting the settings file.
val Context.favoritesDataStore: DataStore<Preferences> by preferencesDataStore(name = "favorites")
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val Context.playlistsDataStore: DataStore<Preferences> by preferencesDataStore(name = "playlists")
