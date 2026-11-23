package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.GuestWorkspaceDao
import com.example.data.local.LocalConversationEntity
import com.example.data.local.LocalMessageEntity
import com.example.data.local.UnknownDatabase
import com.example.data.preferences.SettingsDataStore
import com.example.data.remote.FirestoreWorkspaceSource
import com.example.data.repository.WorkspaceRepository
import com.example.domain.BookmarkKind
import com.example.domain.BookmarkSnippet
import com.example.domain.ChatMessage
import com.example.domain.Conversation
import com.example.domain.Folder
import com.example.domain.MemoryCategory
import com.example.domain.MemoryEntry
import com.example.domain.MessageReaction
import com.example.domain.Persona
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomWorkspaceDatabaseTest {

    private lateinit var context: Context
    private lateinit var db: UnknownDatabase
    private lateinit var dao: GuestWorkspaceDao
    private lateinit var repository: WorkspaceRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        db = Room.inMemoryDatabaseBuilder(context, UnknownDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.guestWorkspaceDao()
        repository = WorkspaceRepository(
            guestDao = dao,
            studioImageDao = db.studioImageDao(),
            remote = FirestoreWorkspaceSource(context),
            settings = SettingsDataStore(context)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `chat sessions and messages persist ordered and cascade delete`() = runTest {
        val now = 1_700_000_000_000L
        dao.upsertConversation(
            LocalConversationEntity(
                id = "sess_1",
                title = "Room Architecture",
                folderId = null,
                pinned = false,
                modelId = "gemini-3.8-flash",
                personaId = "preset_unknown_assistant",
                lastMessagePreview = "Initial preview",
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
        dao.upsertConversation(
            LocalConversationEntity(
                id = "sess_2",
                title = "Pinned Session",
                folderId = null,
                pinned = true,
                modelId = "gemini-4-argon",
                personaId = "preset_code_architect",
                lastMessagePreview = "Pinned",
                createdAtMillis = now - 1000,
                updatedAtMillis = now - 500
            )
        )

        dao.upsertMessage(
            LocalMessageEntity(
                id = "m_2",
                conversationId = "sess_1",
                role = "model",
                content = "Answer",
                attachmentsJoined = "",
                reaction = "liked",
                followUpsJoined = "Next step",
                partialTimeout = false,
                sequenceIndex = 1,
                createdAtMillis = now + 20,
                updatedAtMillis = now + 20
            )
        )
        dao.upsertMessage(
            LocalMessageEntity(
                id = "m_1",
                conversationId = "sess_1",
                role = "user",
                content = "Question",
                attachmentsJoined = "spec.pdf (1.2 MB)",
                reaction = "none",
                followUpsJoined = "",
                partialTimeout = false,
                sequenceIndex = 0,
                createdAtMillis = now + 10,
                updatedAtMillis = now + 10
            )
        )

        val orderedSessions = dao.observeConversations().first()
        assertEquals(listOf("sess_2", "sess_1"), orderedSessions.map { it.id })

        val orderedMessages = dao.observeMessages("sess_1").first()
        assertEquals(listOf("m_1", "m_2"), orderedMessages.map { it.id })

        // Deleting a session cascades to its individual messages via foreign key
        dao.deleteConversation("sess_1")
        assertTrue(dao.getMessagesOnce("sess_1").isEmpty())
    }

    @Test
    fun `guest repository persists sessions, truncates on edit, and detects migration data`() = runTest {
        assertFalse(repository.hasGuestDataToMigrate())

        val folder = Folder(id = "f_1", name = "Kotlin", colorKey = "cobalt", sortOrder = 0)
        repository.saveFolder(uid = null, folder = folder, isNew = true)

        val session = Conversation(
            id = "conv_guest_1",
            title = "Guest Offline Chat",
            folderId = "f_1",
            pinned = false,
            modelId = "gemini-3.8-flash",
            personaId = "preset_unknown_assistant",
            lastMessagePreview = "Hello"
        )
        repository.createConversation(uid = null, conversation = session)

        val m0 = ChatMessage(
            id = "msg_0",
            conversationId = session.id,
            role = "user",
            content = "How do Room flows work?",
            attachments = listOf("notes.md (2 KB)", "schema.kt (4 KB)"),
            sequenceIndex = 0
        )
        val m1 = ChatMessage(
            id = "msg_1",
            conversationId = session.id,
            role = "model",
            content = "Room emits new values whenever observed tables change.",
            reaction = MessageReaction.LIKED,
            followUps = listOf("Show DAO test", "Explain migrations"),
            sequenceIndex = 1
        )
        repository.createMessage(uid = null, message = m0)
        repository.createMessage(uid = null, message = m1)

        repository.updateConversationPreview(uid = null, conversationId = session.id, previewText = m1.content)

        val loadedSession = repository.getConversationOnce(uid = null, conversationId = session.id)
        assertEquals(m1.content, loadedSession?.lastMessagePreview)

        val loadedMsgs = repository.observeMessages(uid = null, conversationId = session.id).first()
        assertEquals(2, loadedMsgs.size)
        assertEquals(listOf("notes.md (2 KB)", "schema.kt (4 KB)"), loadedMsgs[0].attachments)
        assertEquals(listOf("Show DAO test", "Explain migrations"), loadedMsgs[1].followUps)
        assertEquals(MessageReaction.LIKED, loadedMsgs[1].reaction)

        // Truncate from sequence 1 (simulating edit & regenerate)
        repository.deleteMessagesFromSequence(uid = null, conversationId = session.id, fromSequenceIndex = 1)
        val afterTruncate = repository.observeMessages(uid = null, conversationId = session.id).first()
        assertEquals(listOf("msg_0"), afterTruncate.map { it.id })

        // Persona, Memory, and Bookmark persistence
        repository.saveCustomPersona(
            uid = null,
            persona = Persona(id = "p_custom", name = "Reviewer", tagline = "Strict", systemInstruction = "Be concise"),
            isNew = true
        )
        repository.saveMemory(
            uid = null,
            memory = MemoryEntry(id = "mem_1", category = MemoryCategory.PREFERENCE, content = "Use Compose M3"),
            isNew = true
        )
        repository.saveBookmark(
            uid = null,
            bookmark = BookmarkSnippet(
                id = "bm_1",
                kind = BookmarkKind.SNIPPET,
                title = "Room DAO",
                content = "@Dao interface SampleDao",
                language = "kotlin",
                sourceConversationId = session.id
            )
        )

        assertTrue(repository.hasGuestDataToMigrate())
    }

    @Test
    fun `application container provides singleton room database, repository, and viewmodel factories`() {
        val container = context.appContainer
        assertTrue(container.database === container.database)
        assertTrue(container.workspaceRepository === container.workspaceRepository)

        val voiceEngine = container.createVoiceEngine()
        val workspaceFactory = container.workspaceViewModelFactory(uid = null, voiceEngine = voiceEngine)
        val studioFactory = container.imageStudioViewModelFactory(uid = null)
        val authFactory = container.authViewModelFactory()
        val adminFactory = container.adminViewModelFactory(adminUid = "admin_test")

        assertNotNull(workspaceFactory.create(com.example.ui.viewmodel.WorkspaceViewModel::class.java))
        assertNotNull(studioFactory.create(com.example.ui.viewmodel.ImageStudioViewModel::class.java))
        assertNotNull(authFactory.create(com.example.ui.viewmodel.AuthViewModel::class.java))
        assertNotNull(adminFactory.create(com.example.ui.viewmodel.AdminViewModel::class.java))
        voiceEngine.release()
    }
}
