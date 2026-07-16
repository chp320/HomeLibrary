package com.home.library.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.home.library.data.local.enums.UserRole
import com.home.library.data.local.enums.UserStatus
import com.home.library.data.local.view.UserListItem
import com.home.library.data.repository.UserRepository
import com.home.library.session.SessionManager
import com.home.library.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class UserListViewModel @Inject constructor(
    userRepository: UserRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _role = MutableStateFlow<UserRole?>(null)
    private val _status = MutableStateFlow<UserStatus?>(null)

    fun onQueryChange(v: String) { _query.value = v }
    fun onRoleChange(v: UserRole?) { _role.value = v }
    fun onStatusChange(v: UserStatus?) { _status.value = v }

    private val filters = combine(_query, _role, _status) { q, r, s -> Filters(q, r, s) }

    private val users = filters
        .debounce(300L)
        .flatMapLatest { f -> userRepository.searchUsers(f.query, f.role, f.status) }

    val ui: StateFlow<UserListUiState> = combine(
        filters,
        users,
        sessionManager.state,
    ) { f, list, session ->
        UserListUiState(
            query = f.query,
            role = f.role,
            status = f.status,
            users = list,
            isAdmin = (session as? SessionState.LoggedIn)?.role == UserRole.ROLE_ADMIN,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), UserListUiState())

    private data class Filters(val query: String, val role: UserRole?, val status: UserStatus?)
}

data class UserListUiState(
    val query: String = "",
    val role: UserRole? = null,
    val status: UserStatus? = null,
    val users: List<UserListItem> = emptyList(),
    val isAdmin: Boolean = false,
)
