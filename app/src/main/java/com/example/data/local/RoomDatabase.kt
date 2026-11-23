package com.example.data.local

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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "guest_folders")
data class LocalFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorKey: String,
    val sortOrder: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "guest_conversations",
    indices = [
        Index(value = ["folderId"]),
        Index(value = ["pinned", "updatedAtMillis"])
    ]
)
data class LocalConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val folderId: String?,
    val pinned: Boolean,
    val modelId: String,
    val personaId: String,
    val lastMessagePreview: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "guest_messages",
    foreignKeys = [
        ForeignKey(
            entity = LocalConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["conversationId", "sequenceIndex"])
    ]
)
data class LocalMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val attachmentsJoined: String,
    val reaction: String,
    val followUpsJoined: String,
    val partialTimeout: Boolean,
    val sequenceIndex: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(tableName = "guest_personas")
data class LocalPersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val tagline: String,
    val systemInstruction: String,
    val builtIn: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(tableName = "guest_memories")
data class LocalMemoryEntity(
    @PrimaryKey val id: String,
    val category: String,
    val content: String,
    val enabled: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(tableName = "guest_bookmarks")
data class LocalBookmarkEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val content: String,
    val language: String?,
    val sourceConversationId: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(tableName = "studio_images")
data class StudioImageEntity(
    @PrimaryKey val id: String,
    val prompt: String,
    val modelId: String,
    val aspectRatio: String,
    val isEdit: Boolean,
    val localImagePath: String,
    val createdAtMillis: Long
)

@Dao
interface GuestWorkspaceDao {
    @Query("SELECT * FROM guest_folders ORDER BY sortOrder ASC, createdAtMillis ASC")
    fun observeFolders(): Flow<List<LocalFolderEntity>>

    @Query("SELECT * FROM guest_folders")
    suspend fun getAllFoldersOnce(): List<LocalFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: LocalFolderEntity)

    @Query("DELETE FROM guest_folders WHERE id = :id")
    suspend fun deleteFolder(id: String)

    @Query("SELECT * FROM guest_conversations ORDER BY pinned DESC, updatedAtMillis DESC")
    fun observeConversations(): Flow<List<LocalConversationEntity>>

    @Query("SELECT * FROM guest_conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationOnce(id: String): LocalConversationEntity?

    @Query("SELECT * FROM guest_conversations")
    suspend fun getAllConversationsOnce(): List<LocalConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: LocalConversationEntity)

    @Query("UPDATE guest_conversations SET lastMessagePreview = :preview, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun updateConversationPreview(id: String, preview: String, updatedAtMillis: Long)

    @Query("DELETE FROM guest_conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("UPDATE guest_conversations SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolderFromConversations(folderId: String)

    @Query("SELECT * FROM guest_messages WHERE conversationId = :conversationId ORDER BY sequenceIndex ASC, createdAtMillis ASC")
    fun observeMessages(conversationId: String): Flow<List<LocalMessageEntity>>

    @Query("SELECT * FROM guest_messages WHERE conversationId = :conversationId ORDER BY sequenceIndex ASC, createdAtMillis ASC")
    suspend fun getMessagesOnce(conversationId: String): List<LocalMessageEntity>

    @Query("SELECT * FROM guest_messages")
    suspend fun getAllMessagesOnce(): List<LocalMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: LocalMessageEntity)

    @Query("DELETE FROM guest_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("DELETE FROM guest_messages WHERE conversationId = :conversationId AND sequenceIndex >= :fromSequenceIndex")
    suspend fun deleteMessagesFromSequence(conversationId: String, fromSequenceIndex: Int)

    @Query("SELECT * FROM guest_personas ORDER BY createdAtMillis ASC")
    fun observePersonas(): Flow<List<LocalPersonaEntity>>

    @Query("SELECT * FROM guest_personas")
    suspend fun getAllPersonasOnce(): List<LocalPersonaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPersona(persona: LocalPersonaEntity)

    @Query("DELETE FROM guest_personas WHERE id = :id")
    suspend fun deletePersona(id: String)

    @Query("SELECT * FROM guest_memories ORDER BY updatedAtMillis DESC")
    fun observeMemories(): Flow<List<LocalMemoryEntity>>

    @Query("SELECT * FROM guest_memories")
    suspend fun getAllMemoriesOnce(): List<LocalMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemory(memory: LocalMemoryEntity)

    @Query("DELETE FROM guest_memories WHERE id = :id")
    suspend fun deleteMemory(id: String)

    @Query("SELECT * FROM guest_bookmarks ORDER BY updatedAtMillis DESC")
    fun observeBookmarks(): Flow<List<LocalBookmarkEntity>>

    @Query("SELECT * FROM guest_bookmarks")
    suspend fun getAllBookmarksOnce(): List<LocalBookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(bookmark: LocalBookmarkEntity)

    @Query("DELETE FROM guest_bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: String)

    @Query("DELETE FROM guest_messages")
    suspend fun clearAllMessages()

    @Query("DELETE FROM guest_conversations")
    suspend fun clearAllConversations()

    @Query("DELETE FROM guest_folders")
    suspend fun clearAllFolders()

    @Query("DELETE FROM guest_personas")
    suspend fun clearAllPersonas()

    @Query("DELETE FROM guest_memories")
    suspend fun clearAllMemories()

    @Query("DELETE FROM guest_bookmarks")
    suspend fun clearAllBookmarks()
}

@Dao
interface StudioImageDao {
    @Query("SELECT * FROM studio_images ORDER BY createdAtMillis DESC")
    fun observeImages(): Flow<List<StudioImageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: StudioImageEntity)

    @Query("DELETE FROM studio_images WHERE id = :id")
    suspend fun deleteImage(id: String)
}

@Database(
    entities = [
        LocalFolderEntity::class,
        LocalConversationEntity::class,
        LocalMessageEntity::class,
        LocalPersonaEntity::class,
        LocalMemoryEntity::class,
        LocalBookmarkEntity::class,
        StudioImageEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class UnknownDatabase : RoomDatabase() {
    abstract fun guestWorkspaceDao(): GuestWorkspaceDao
    abstract fun studioImageDao(): StudioImageDao

    companion object {
        @Volatile
        private var instance: UnknownDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_guest_conversations_folderId` ON `guest_conversations` (`folderId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_guest_conversations_pinned_updatedAtMillis` ON `guest_conversations` (`pinned`, `updatedAtMillis`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `guest_messages_new` (
                        `id` TEXT NOT NULL,
                        `conversationId` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `attachmentsJoined` TEXT NOT NULL,
                        `reaction` TEXT NOT NULL,
                        `followUpsJoined` TEXT NOT NULL,
                        `partialTimeout` INTEGER NOT NULL,
                        `sequenceIndex` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`conversationId`) REFERENCES `guest_conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `guest_messages_new` (`id`, `conversationId`, `role`, `content`, `attachmentsJoined`, `reaction`, `followUpsJoined`, `partialTimeout`, `sequenceIndex`, `createdAtMillis`, `updatedAtMillis`)
                    SELECT m.`id`, m.`conversationId`, m.`role`, m.`content`, m.`attachmentsJoined`, m.`reaction`, m.`followUpsJoined`, m.`partialTimeout`, m.`sequenceIndex`, m.`createdAtMillis`, m.`updatedAtMillis`
                    FROM `guest_messages` m
                    INNER JOIN `guest_conversations` c ON m.`conversationId` = c.`id`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `guest_messages`")
                db.execSQL("ALTER TABLE `guest_messages_new` RENAME TO `guest_messages`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_guest_messages_conversationId_sequenceIndex` ON `guest_messages` (`conversationId`, `sequenceIndex`)")
            }
        }

        fun getInstance(context: Context): UnknownDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    UnknownDatabase::class.java,
                    "unknown_ai_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
