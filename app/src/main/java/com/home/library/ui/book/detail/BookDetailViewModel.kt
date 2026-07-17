package com.home.library.ui.book.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.home.library.data.local.entity.BookEntity
import com.home.library.data.local.enums.UserRole
import com.home.library.data.local.view.BookLoanHistoryView
import com.home.library.data.repository.BookRepository
import com.home.library.data.repository.DiscardResult
import com.home.library.data.repository.LoanRepository
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val loanRepository: LoanRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val bookId: Long = savedStateHandle[Routes.BOOK_DETAIL_ARG_ID] ?: Routes.BOOK_EDIT_NEW_ID
    private val _event = MutableStateFlow<DetailEvent?>(null)

    /** 이 도서의 대출 이력(append-only). BOOK-008. */
    val history: StateFlow<List<BookLoanHistoryView>> =
        loanRepository.bookLoanHistory(bookId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /**
     * 이 도서를 본인이 대출 중인지(A-5). 활성 대출 Flow에서 파생하므로
     * 대출 화면에서 대출 후 pop으로 복귀했을 때 자동 갱신된다.
     * 세션은 터치마다 재emit되므로 userId만 뽑아 distinctUntilChanged로 끊는다.
     */
    private val borrowedByMe: Flow<Boolean> = sessionManager.state
        .map { (it as? SessionState.LoggedIn)?.userId }
        .distinctUntilChanged()
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf(false)
            } else {
                loanRepository.activeLoans(userId).map { loans -> loans.any { it.bookId == bookId } }
            }
        }

    val ui: StateFlow<BookDetailUiState> = combine(
        bookRepository.flowById(bookId),
        sessionManager.state,
        _event,
        borrowedByMe,
    ) { book, session, event, borrowed ->
        BookDetailUiState(
            book = book,
            isAdmin = (session as? SessionState.LoggedIn)?.role == UserRole.ROLE_ADMIN,
            event = event,
            isBorrowedByMe = borrowed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), BookDetailUiState())

    val editBookId: Long get() = bookId

    fun discard() {
        if (!ui.value.isAdmin) return
        viewModelScope.launch {
            val event = when (bookRepository.discard(bookId)) {
                DiscardResult.Success, DiscardResult.NotFound -> DetailEvent.Deleted
                DiscardResult.HasActiveLoans -> DetailEvent.HasActiveLoans
            }
            _event.value = event
        }
    }

    fun onEventHandled() { _event.value = null }
}

data class BookDetailUiState(
    val book: BookEntity? = null,
    val isAdmin: Boolean = false,
    val event: DetailEvent? = null,
    /** 본인이 이 도서를 대출 중. "대출 중"(본인) vs "대출 불가"(가용 0, 타인 대출) 구분용. */
    val isBorrowedByMe: Boolean = false,
)

sealed interface DetailEvent {
    data object Deleted : DetailEvent
    data object HasActiveLoans : DetailEvent
}
