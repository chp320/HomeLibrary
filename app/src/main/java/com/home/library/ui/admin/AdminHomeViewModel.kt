package com.home.library.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.home.library.data.local.enums.UserRole
import com.home.library.session.SessionManager
import com.home.library.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AdminHomeViewModel @Inject constructor(
    sessionManager: SessionManager,
) : ViewModel() {
    val isAdmin: StateFlow<Boolean> = sessionManager.state
        .map { (it as? SessionState.LoggedIn)?.role == UserRole.ROLE_ADMIN }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), false)
}
