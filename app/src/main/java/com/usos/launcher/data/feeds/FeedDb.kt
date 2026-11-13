package com.usos.launcher.data.feeds

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

/**
 * Sin clave foránea: al borrar un feed los artículos guardados sobreviven con `feedId = -id` (único) y su
 * [sourceTitle]; el resto se borra a mano.
 */
@Entity(
    tableName = "articles",
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
    /** Sin uso (heredado del límite por hora); se conserva la columna para no migrar la base. */
    val revealedAt: Long? = null,
    /** Nombre de la fuente cuando el feed ya no existe (guardados huérfanos); vacío mientras el feed existe. */
    @ColumnInfo(defaultValue = "''") val sourceTitle: String = "",
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

    /** Desvincula los guardados de un feed que se va a borrar (conservan el nombre de la fuente). */
    @Query("UPDATE articles SET sourceTitle = (SELECT title FROM feeds WHERE id = :feedId), feedId = -id WHERE feedId = :feedId AND starred = 1")
    suspend fun orphanStarred(feedId: Long)

    @Query("DELETE FROM articles WHERE feedId = :feedId")
    suspend fun deleteArticlesOf(feedId: Long)

    @Query("UPDATE articles SET read = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE articles SET starred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)

    /** Conserva los [keep] más recientes de la fuente, más todo lo guardado con estrella. */
    @Query(
        "DELETE FROM articles WHERE feedId = :feedId AND starred = 0 AND id NOT IN " +
            "(SELECT id FROM articles WHERE feedId = :feedId ORDER BY publishedAt DESC, id DESC LIMIT :keep)",
    )
    suspend fun prune(feedId: Long, keep: Int)
}

@Database(entities = [Feed::class, Article::class], version = 2, exportSchema = false)
abstract class FeedDatabase : RoomDatabase() {
    abstract fun dao(): FeedDao

    companion object {
        fun create(context: Context): FeedDatabase =
            Room.databaseBuilder(context.applicationContext, FeedDatabase::class.java, "feeds.db")
                .addMigrations(MIGRATION_1_2).build()

        /** v1 -> v2: quita la clave foránea en cascada y añade `sourceTitle`. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE articles_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, feedId INTEGER NOT NULL, " +
                        "title TEXT NOT NULL, summary TEXT NOT NULL, url TEXT NOT NULL, imageUrl TEXT, " +
                        "publishedAt INTEGER NOT NULL, read INTEGER NOT NULL, starred INTEGER NOT NULL, " +
                        "revealedAt INTEGER, sourceTitle TEXT NOT NULL DEFAULT '')",
                )
                db.execSQL(
                    "INSERT INTO articles_new (id, feedId, title, summary, url, imageUrl, publishedAt, read, starred, revealedAt) " +
                        "SELECT id, feedId, title, summary, url, imageUrl, publishedAt, read, starred, revealedAt FROM articles",
                )
                db.execSQL("DROP TABLE articles")
                db.execSQL("ALTER TABLE articles_new RENAME TO articles")
                db.execSQL("CREATE UNIQUE INDEX index_articles_feedId_url ON articles (feedId, url)")
                db.execSQL("CREATE INDEX index_articles_feedId ON articles (feedId)")
            }
        }
    }
}
