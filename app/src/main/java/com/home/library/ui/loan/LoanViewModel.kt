package com.home.library.ui.loan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.home.library.data.local.entity.BookEntity
import com.home.library.data.local.enums.UserRole
import com.home.library.data.repository.BookRepository
import com.home.library.data.repository.LoanRepository
import com.home.library.data.repository.LoanResult
import com.home.library.loan.LoanAllowance
import com.home.library.session.SessionManager
import com.home.library.session.SessionState
import com.home.library.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LoanViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val loanRepository: LoanRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val argBookId: Long = savedStateHandle[Routes.LOAN_ARG_BOOK_ID] ?: Routes.LOAN_NO_BOOK_ID

    private val _internal = MutableStateFlow(LoanInternal())

    /** 대출 기간(일). AppConfig 조회라 한 박자 늦으므로 null이면 반납 예정일을 표시하지 않는다. */
    private val _periodDays = MutableStateFlow<Long?>(null)

    /**
     * 세션 + 대출 여력. 세션은 터치마다 lastActivityAt이 바뀐 인스턴스를 emit하므로
     * 표시에 쓰는 값만 뽑아 distinctUntilChanged로 끊는다(그러지 않으면 터치마다 여력을 재조회).
     */
    private val userInfo: Flow<LoanUserInfo?> = sessionManager.state
        .map { s -> (s as? SessionState.LoggedIn)?.let { SessionInfo(it.userId, it.name, it.role) } }
        .distinctUntilChanged()
        .flatMapLatest { info ->
            if (info == null) {
                flowOf(null)
            } else {
                loanRepository.allowance(info.userId)
                    .map<LoanAllowance, LoanAllowance?> { it }
                    .onStart { emit(null) }
                    .map { allowance -> LoanUserInfo(info.name, info.role, allowance) }
            }
        }

    val ui: StateFlow<LoanUiState> = combine(
        _internal,
        userInfo,
        _periodDays,
    ) { internal, user, periodDays ->
        LoanUiState(
            book = internal.book,
            borrowerName = user?.name,
            allowance = user?.allowance,
            periodDays = periodDays,
            lastIsbn = internal.lastIsbn,
            message = internal.message,
            loading = internal.loading,
            success = internal.success,
            isLoggedIn = user != null,
            isAdmin = user?.role == UserRole.ROLE_ADMIN,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), LoanUiState())

    init {
        if (argBookId != Routes.LOAN_NO_BOOK_ID) {
            viewModelScope.launch {
                val book = bookRepository.getById(argBookId)
                _internal.update { it.copy(book = book) }
            }
        }
        viewModelScope.launch { _periodDays.value = loanRepository.loanPeriodDays() }
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

    /** 세션에서 표시에 쓰는 값만. lastActivityAt 제외. */
    private data class SessionInfo(val userId: Long, val name: String, val role: UserRole)

    private data class LoanUserInfo(val name: String, val role: UserRole, val allowance: LoanAllowance?)
}

data class LoanUiState(
    val book: BookEntity? = null,
    val borrowerName: String? = null,
    /** 대출자의 대출 여력. 조회 전이면 null. */
    val allowance: LoanAllowance? = null,
    /** 대출 기간(일). 조회 전이면 null. */
    val periodDays: Long? = null,
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
