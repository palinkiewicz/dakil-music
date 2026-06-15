package pl.dakil.music.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [ListeningRecordEntity::class], version = 1, exportSchema = true)
@TypeConverters(ArtistsConverter::class)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun listeningRecordDao(): ListeningRecordDao
}
