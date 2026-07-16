package com.home.library.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.home.library.data.local.enums.UserRole
import com.home.library.data.local.view.AdminLoanView
import com.home.library.data.repository.LoanRepository
import com.home.library.session.SessionManager
import com.home.library.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AdminLoanStatusViewModel @Inject constructor(
    loanRepository: LoanRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    val ui: StateFlow<AdminLoanStatusUiState> = combine(
        sessionManager.state,
        loanRepository.allActiveLoans(),
    ) { session, loans ->
        AdminLoanStatusUiState(
            isAdmin = (session as? SessionState.LoggedIn)?.role == UserRole.ROLE_ADMIN,
            loans = loans,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AdminLoanStatusUiState())
}

data class AdminLoanStatusUiState(
    val isAdmin: Boolean = false,
    val loans: List<AdminLoanView> = emptyList(),
)
