package com.example.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.ai.GeminiAiRepository
import com.example.data.local.GuestWorkspaceDao
import com.example.data.local.StudioImageDao
import com.example.data.local.UnknownDatabase
import com.example.data.preferences.SettingsDataStore
import com.example.data.remote.FirestoreWorkspaceSource
import com.example.data.repository.WorkspaceRepository
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.ImageStudioViewModel
import com.example.ui.viewmodel.WorkspaceViewModel
import com.example.util.VoiceEngine
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Application-scoped dependency injection contract providing single-instance
 * persistence, remote sync, AI services, and ViewModel factories.
 */
interface AppContainer {
    val database: UnknownDatabase
    val guestWorkspaceDao: GuestWorkspaceDao
    val studioImageDao: StudioImageDao
    val settingsDataStore: SettingsDataStore
    val firestoreWorkspaceSource: FirestoreWorkspaceSource
    val workspaceRepository: WorkspaceRepository
    val geminiAiRepository: GeminiAiRepository

    fun createVoiceEngine(): VoiceEngine
    fun authViewModelFactory(): ViewModelProvider.Factory
    fun workspaceViewModelFactory(uid: String?, voiceEngine: VoiceEngine): ViewModelProvider.Factory
    fun imageStudioViewModelFactory(uid: String?): ViewModelProvider.Factory
    fun adminViewModelFactory(adminUid: String): ViewModelProvider.Factory
}

/**
 * Default production implementation owned by [com.example.UnknownAiApplication].
 * Dependencies are initialized lazily so startup stays fast and Firebase App Check
 * can be configured in MainActivity before any Firestore access occurs.
 */
class DefaultAppContainer(context: Context) : AppContainer {

    private val appContext = context.applicationContext

    override val database: UnknownDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        UnknownDatabase.getInstance(appContext)
    }

    override val guestWorkspaceDao: GuestWorkspaceDao
        get() = database.guestWorkspaceDao()

    override val studioImageDao: StudioImageDao
        get() = database.studioImageDao()

    override val settingsDataStore: SettingsDataStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SettingsDataStore(appContext)
    }

    override val firestoreWorkspaceSource: FirestoreWorkspaceSource by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val firestoreDb = FirebaseFirestore.getInstance(
            appContext.getString(R.string.firestore_database_id)
        )
        FirestoreWorkspaceSource(firestoreDb)
    }

    override val workspaceRepository: WorkspaceRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WorkspaceRepository(
            guestDao = guestWorkspaceDao,
            studioImageDao = studioImageDao,
            remote = firestoreWorkspaceSource,
            settings = settingsDataStore
        )
    }

    override val geminiAiRepository: GeminiAiRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        GeminiAiRepository()
    }

    override fun createVoiceEngine(): VoiceEngine = VoiceEngine(appContext)

    override fun authViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            AuthViewModel(
                repository = workspaceRepository,
                settings = settingsDataStore
            )
        }
    }

    override fun workspaceViewModelFactory(
        uid: String?,
        voiceEngine: VoiceEngine
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            WorkspaceViewModel(
                uid = uid,
                repository = workspaceRepository,
                aiRepository = geminiAiRepository,
                settings = settingsDataStore,
                voiceEngine = voiceEngine
            )
        }
    }

    override fun imageStudioViewModelFactory(uid: String?): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            ImageStudioViewModel(
                uid = uid,
                repository = workspaceRepository,
                aiRepository = geminiAiRepository
            )
        }
    }

    override fun adminViewModelFactory(adminUid: String): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            AdminViewModel(
                adminUid = adminUid,
                repository = workspaceRepository
            )
        }
    }
}
