package com.usos.launcher.data.feeds

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "feeds", indices = [Index(value = ["url"], unique = true)])
data class Feed(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val lastFetchedAt: Long = 0,
)

@Entity(
    tableName = "articles",
    foreignKeys = [ForeignKey(Feed::class, ["id"], ["feedId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["feedId", "url"], unique = true), Index("feedId")],
)
data class Article(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedId: Long,
    val title: String,
    val summary: String,
    val url: String,
    val imageUrl: String?,
    val publishedAt: Long,
    val read: Boolean = false,
    val starred: Boolean = false,
    /** Momento en que el panel desveló el artículo; null = en cola. */
    val revealedAt: Long? = null,
)

@Dao
interface FeedDao {
    @Query("SELECT * FROM feeds ORDER BY id")
    fun feeds(): Flow<List<Feed>>

    @Query("SELECT * FROM feeds ORDER BY id")
    suspend fun feedsNow(): List<Feed>

    @Query("SELECT * FROM articles ORDER BY publishedAt DESC, id DESC")
    fun articles(): Flow<List<Article>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFeed(feed: Feed): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticles(articles: List<Article>): List<Long>

    @Query("UPDATE feeds SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE feeds SET lastFetchedAt = :at WHERE id = :id")
    suspend fun setFetched(id: Long, at: Long)

    @Query("DELETE FROM feeds WHERE id = :id")
    suspend fun deleteFeed(id: Long)

    @Query("UPDATE articles SET read = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE articles SET starred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)

    @Query("UPDATE articles SET revealedAt = :at WHERE id IN (:ids) AND revealedAt IS NULL")
    suspend fun reveal(ids: List<Long>, at: Long)

    /** Conserva los 100 más recientes de la fuente, más todo lo guardado con estrella. */
    @Query(
        "DELETE FROM articles WHERE feedId = :feedId AND starred = 0 AND id NOT IN " +
            "(SELECT id FROM articles WHERE feedId = :feedId ORDER BY publishedAt DESC, id DESC LIMIT 100)",
    )
    suspend fun prune(feedId: Long)
}

@Database(entities = [Feed::class, Article::class], version = 1, exportSchema = false)
abstract class FeedDatabase : RoomDatabase() {
    abstract fun dao(): FeedDao

    companion object {
        fun create(context: Context): FeedDatabase =
            Room.databaseBuilder(context.applicationContext, FeedDatabase::class.java, "feeds.db").build()
    }
}
