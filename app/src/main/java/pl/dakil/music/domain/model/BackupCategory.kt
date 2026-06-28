package pl.dakil.music.domain.model

/**
 * A single, independently importable/exportable slice of app data.
 *
 * Each category is backed by a Preferences DataStore and serialized generically.
 * Listening history is intentionally *not* here — it has its own import/export on the
 * listening-history screen. [fileName] is both the suggested document name for a
 * single-category export and the entry name inside a full-backup zip.
 */
enum class BackupCategory(val fileName: String) {
    SETTINGS("settings.csv"),
    FAVORITES("favorites.csv"),
    PLAYLISTS("playlists.csv"),
    ALBUM_RULES("album_rules.csv"),
    LYRICS_ALIGNMENT("lyrics_alignment.csv"),
    SORT("sort.csv"),
    ;

    companion object {
        /** The category whose [fileName] matches [name], or null for an unknown entry. */
        fun byFileName(name: String): BackupCategory? = entries.firstOrNull { it.fileName == name }
    }
}
