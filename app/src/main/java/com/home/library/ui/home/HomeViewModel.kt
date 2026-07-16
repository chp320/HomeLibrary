package com.home.library.ui.home

import androidx.lifecycle.ViewModel
import com.home.library.session.SessionManager
import com.home.library.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionManager: SessionManager,
) : ViewModel() {

    val session: StateFlow<SessionState> = sessionManager.state

    fun logout() = sessionManager.clear()
}
