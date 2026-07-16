package com.home.library.ui.loan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.home.library.data.local.entity.BookEntity
import com.home.library.data.local.enums.UserRole
import com.home.library.data.repository.BookRepository
import com.home.library.data.repository.LoanRepository
import com.home.library.data.repository.LoanResult
import com.home.library.session.SessionManager
import com.home.library.session.SessionState
import com.home.library.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoanViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val loanRepository: LoanRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val argBookId: Long = savedStateHandle[Routes.LOAN_ARG_BOOK_ID] ?: Routes.LOAN_NO_BOOK_ID

    private val _internal = MutableStateFlow(LoanInternal())

    val ui: StateFlow<LoanUiState> = combine(_internal, sessionManager.state) { internal, session ->
        val loggedIn = session as? SessionState.LoggedIn
        LoanUiState(
            book = internal.book,
            borrowerName = loggedIn?.name,
            lastIsbn = internal.lastIsbn,
            message = internal.message,
            loading = internal.loading,
            success = internal.success,
            isLoggedIn = loggedIn != null,
            isAdmin = loggedIn?.role == UserRole.ROLE_ADMIN,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), LoanUiState())

    init {
        if (argBookId != Routes.LOAN_NO_BOOK_ID) {
            viewModelScope.launch {
                val book = bookRepository.getById(argBookId)
                _internal.update { it.copy(book = book) }
            }
        }
    }

    /** 스캔/입력된 ISBN을 로컬에서만 조회한다(대출은 보유 도서만 대상 → API 호출 금지). */
    fun onIsbnScanned(isbn: String) {
        viewModelScope.launch {
            val book = bookRepository.findActiveByIsbn(isbn)
            _internal.update {
                if (book == null) {
                    it.copy(book = null, lastIsbn = isbn, message = LoanMessage.NotRegistered)
                } else {
                    it.copy(book = book, lastIsbn = isbn, message = null)
                }
            }
        }
    }

    fun confirmLoan() {
        val session = sessionManager.state.value as? SessionState.LoggedIn ?: return
        val book = _internal.value.book ?: return
        if (_internal.value.loading) return
        viewModelScope.launch {
            _internal.update { it.copy(loading = true, message = null) }
            // 트랜잭션 동안 자동 로그아웃 유예(try/finally 내장)
            val result = sessionManager.withCriticalSection {
                loanRepository.loan(session.userId, book.bookId)
            }
            _internal.update {
                when (result) {
                    is LoanResult.Success -> it.copy(loading = false, success = true)
                    LoanResult.BookUnavailable -> it.copy(loading = false, message = LoanMessage.BookUnavailable)
                    LoanResult.AlreadyBorrowed -> it.copy(loading = false, message = LoanMessage.AlreadyBorrowed)
                    LoanResult.HasOverdue -> it.copy(loading = false, message = LoanMessage.HasOverdue)
                    is LoanResult.MaxCountExceeded -> it.copy(loading = false, message = LoanMessage.MaxCount(result.max))
                    LoanResult.NotAvailable -> it.copy(loading = false, message = LoanMessage.NotAvailable)
                }
            }
        }
    }

    private fun MutableStateFlow<LoanInternal>.update(f: (LoanInternal) -> LoanInternal) {
        value = f(value)
    }

    private data class LoanInternal(
        val book: BookEntity? = null,
        val lastIsbn: String? = null,
        val message: LoanMessage? = null,
        val loading: Boolean = false,
        val success: Boolean = false,
    )
}

data class LoanUiState(
    val book: BookEntity? = null,
    val borrowerName: String? = null,
    val lastIsbn: String? = null,
    val message: LoanMessage? = null,
    val loading: Boolean = false,
    val success: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isAdmin: Boolean = false,
)

sealed interface LoanMessage {
    data object NotRegistered : LoanMessage
    data object BookUnavailable : LoanMessage
    data object AlreadyBorrowed : LoanMessage
    data object HasOverdue : LoanMessage
    data class MaxCount(val max: Int) : LoanMessage
    data object NotAvailable : LoanMessage
}
