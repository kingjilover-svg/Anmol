package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.repository.WorkspaceRepository
import com.example.domain.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AdminUsersUiState {
    data object Loading : AdminUsersUiState
    data class Success(val users: List<UserProfile>) : AdminUsersUiState
    data class Error(val message: String) : AdminUsersUiState
}

class AdminViewModel(
    private val adminUid: String,
    private val repository: WorkspaceRepository
) : ViewModel() {

    val usersState: StateFlow<AdminUsersUiState> = repository.observeAllUsersForAdmin(adminUid)
        .map<List<UserProfile>, AdminUsersUiState> { AdminUsersUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w("AdminViewModel", "Failed to load admin users", root)
            emit(AdminUsersUiState.Error(root.localizedMessage ?: "Admin access denied or unavailable."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AdminUsersUiState.Loading)

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun updateUserRoleAndTier(targetUserId: String, newRole: String, newPlanTier: String) {
        viewModelScope.launch {
            repository.adminUpdateUser(targetUserId, newRole, newPlanTier)
                .onSuccess { _actionMessage.value = "Updated user permissions." }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Update failed." }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    companion object {
        fun provideFactory(
            adminUid: String,
            repository: WorkspaceRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AdminViewModel(adminUid, repository) }
        }
    }
}
