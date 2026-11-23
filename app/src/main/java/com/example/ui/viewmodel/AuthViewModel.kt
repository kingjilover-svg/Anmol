package com.example.ui.viewmodel

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.WorkspaceRepository
import com.example.ui.auth.GoogleAuthHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface AuthSessionState {
    data object Checking : AuthSessionState
    data object Welcome : AuthSessionState
    data object GuestActive : AuthSessionState
    data class Migrating(val uid: String) : AuthSessionState
    data class SignedIn(val uid: String, val migratedCount: Int = 0) : AuthSessionState
}

class AuthViewModel(
    private val repository: WorkspaceRepository,
    private val settings: SettingsDataStore
) : ViewModel() {

    private val auth = Firebase.auth
    private val _sessionState = MutableStateFlow<AuthSessionState>(AuthSessionState.Checking)
    val sessionState: StateFlow<AuthSessionState> = _sessionState.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            handleSignedInUser(user)
        }
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    fun initializeAuth(context: Context) {
        val current = auth.currentUser
        if (current != null) {
            handleSignedInUser(current)
            return
        }
        viewModelScope.launch {
            val prefs = settings.preferencesFlow.first()
            if (prefs.guestSessionActive) {
                _sessionState.value = AuthSessionState.GuestActive
                return@launch
            }
            val cm = CredentialManager.create(context)
            GoogleAuthHelper.attemptAutoSignIn(
                context = context,
                credentialManager = cm,
                scope = viewModelScope,
                onAuthSuccess = {
                    auth.currentUser?.let { handleSignedInUser(it) }
                },
                onUnauthenticated = {
                    if (auth.currentUser == null) {
                        _sessionState.value = AuthSessionState.Welcome
                    }
                }
            )
        }
    }

    fun continueAsGuest() {
        viewModelScope.launch {
            settings.setGuestSessionActive(true)
            _authError.value = null
            _sessionState.value = AuthSessionState.GuestActive
        }
    }

    fun exitGuestToWelcome() {
        viewModelScope.launch {
            settings.setGuestSessionActive(false)
            _sessionState.value = AuthSessionState.Welcome
        }
    }

    fun onSignedInFromButton() {
        auth.currentUser?.let { handleSignedInUser(it) }
    }

    fun setAuthError(message: String?) {
        _authError.value = message
    }

    fun signOut(context: Context) {
        val cm = CredentialManager.create(context)
        GoogleAuthHelper.signOut(cm, viewModelScope) {
            viewModelScope.launch {
                settings.setGuestSessionActive(false)
                _sessionState.value = AuthSessionState.Welcome
            }
        }
    }

    private fun handleSignedInUser(user: FirebaseUser) {
        val currentState = _sessionState.value
        if (currentState is AuthSessionState.SignedIn && currentState.uid == user.uid) return
        if (currentState is AuthSessionState.Migrating && currentState.uid == user.uid) return

        viewModelScope.launch {
            _authError.value = null
            repository.ensureRemoteProfile(user)
            val hasGuest = repository.hasGuestDataToMigrate()
            if (hasGuest) {
                _sessionState.value = AuthSessionState.Migrating(user.uid)
                val migrationResult = repository.migrateGuestWorkspaceToCloud(user.uid)
                val migratedCount = migrationResult.getOrDefault(0)
                if (migrationResult.isFailure) {
                    _authError.value = "Some guest items could not migrate: ${migrationResult.exceptionOrNull()?.localizedMessage}"
                }
                _sessionState.value = AuthSessionState.SignedIn(user.uid, migratedCount)
            } else {
                settings.setGuestSessionActive(false)
                _sessionState.value = AuthSessionState.SignedIn(user.uid, 0)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authListener)
    }

    companion object {
        fun provideFactory(
            repository: WorkspaceRepository,
            settings: SettingsDataStore
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(repository, settings) }
        }
    }
}
