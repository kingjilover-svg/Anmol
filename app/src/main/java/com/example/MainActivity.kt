package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.configureAppCheck
import com.example.data.preferences.AppPreferences
import com.example.domain.ThemeMode
import com.example.ui.auth.GoogleAuthHelper
import com.example.ui.screens.AdaptiveWorkspaceShell
import com.example.ui.screens.LoadingMigrationScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.UnknownAiTheme
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.AuthSessionState
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.ImageStudioViewModel
import com.example.ui.viewmodel.WorkspaceViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent BEFORE Compose UI, ViewModels, or Firebase ops
        configureAppCheck(this, intent)

        val container = applicationContext.appContainer
        val settingsDataStore = container.settingsDataStore

        setContent {
            val prefs by settingsDataStore.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = AppPreferences()
            )
            val isDark = when (prefs.themeMode) {
                ThemeMode.DAYLIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.AUTO -> isSystemInDarkTheme()
            }

            // Synchronize edge-to-edge system bars with Daylight/Dark/Auto theme
            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    }
                )
            }

            UnknownAiTheme(themeMode = prefs.themeMode) {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val authViewModel: AuthViewModel = viewModel(
                    factory = container.authViewModelFactory()
                )
                val sessionState by authViewModel.sessionState.collectAsStateWithLifecycle()
                val authError by authViewModel.authError.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    authViewModel.initializeAuth(context)
                }

                when (val session = sessionState) {
                    is AuthSessionState.Checking -> {
                        LoadingMigrationScreen("Initializing UNKNOWN AI...")
                    }
                    is AuthSessionState.Welcome -> {
                        WelcomeScreen(
                            authError = authError,
                            onGoogleSignInSuccess = { authViewModel.onSignedInFromButton() },
                            onGoogleSignInError = { msg -> authViewModel.setAuthError(msg) },
                            onContinueAsGuest = { authViewModel.continueAsGuest() }
                        )
                    }
                    is AuthSessionState.Migrating -> {
                        LoadingMigrationScreen("Migrating your Guest workspace to Cloud Firestore...")
                    }
                    is AuthSessionState.GuestActive,
                    is AuthSessionState.SignedIn -> {
                        val activeUid = (session as? AuthSessionState.SignedIn)?.uid
                        val sessionKey = activeUid ?: "guest"

                        key(sessionKey) {
                            val voiceEngine = remember(sessionKey) { container.createVoiceEngine() }
                            DisposableEffect(voiceEngine) {
                                onDispose { voiceEngine.release() }
                            }
                            val workspaceViewModel: WorkspaceViewModel = viewModel(
                                key = "workspace_$sessionKey",
                                factory = container.workspaceViewModelFactory(
                                    uid = activeUid,
                                    voiceEngine = voiceEngine
                                )
                            )
                            val imageStudioViewModel: ImageStudioViewModel = viewModel(
                                key = "studio_$sessionKey",
                                factory = container.imageStudioViewModelFactory(uid = activeUid)
                            )
                            val userProfile by workspaceViewModel.userProfile.collectAsStateWithLifecycle()
                            val adminViewModel: AdminViewModel? = if (activeUid != null && userProfile?.isAdmin == true) {
                                viewModel(
                                    key = "admin_$activeUid",
                                    factory = container.adminViewModelFactory(activeUid)
                                )
                            } else null

                            LaunchedEffect(session) {
                                if (session is AuthSessionState.SignedIn && session.migratedCount > 0) {
                                    workspaceViewModel.showMessage(
                                        "Migrated ${session.migratedCount} guest items to your Google account."
                                    )
                                }
                            }

                            AdaptiveWorkspaceShell(
                                workspaceViewModel = workspaceViewModel,
                                imageStudioViewModel = imageStudioViewModel,
                                adminViewModel = adminViewModel,
                                onGoogleSignInFromGuest = {
                                    val cm = CredentialManager.create(context)
                                    GoogleAuthHelper.onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = cm,
                                        scope = scope,
                                        onAuthSuccess = { authViewModel.onSignedInFromButton() },
                                        onAuthError = { msg -> workspaceViewModel.showMessage(msg) }
                                    )
                                },
                                onSignOut = { authViewModel.signOut(context) }
                            )
                        }
                    }
                }
            }
        }
    }
}
