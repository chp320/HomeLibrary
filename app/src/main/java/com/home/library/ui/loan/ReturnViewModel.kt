package com.home.library.ui.loan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.home.library.data.local.view.ActiveLoanView
import com.home.library.data.repository.LoanRepository
import com.home.library.session.SessionManager
import com.home.library.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReturnViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val ui: StateFlow<ReturnUiState> = sessionManager.state.flatMapLatest { session ->
        val loggedIn = session as? SessionState.LoggedIn
        if (loggedIn == null) {
            flowOf(ReturnUiState(isLoggedIn = false))
        } else {
            loanRepository.activeLoans(loggedIn.userId).map { loans ->
                ReturnUiState(isLoggedIn = true, loans = loans)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ReturnUiState())

    fun returnBook(loanId: Long) {
        val session = sessionManager.state.value as? SessionState.LoggedIn ?: return
        viewModelScope.launch {
            // 트랜잭션 동안 자동 로그아웃 유예. 목록은 Flow라 반납 후 자동 갱신.
            sessionManager.withCriticalSection {
                loanRepository.returnBook(loanId, session.userId)
            }
        }
    }
}

data class ReturnUiState(
    val isLoggedIn: Boolean = false,
    val loans: List<ActiveLoanView> = emptyList(),
)
