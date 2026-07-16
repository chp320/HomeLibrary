package com.home.library.ui.book.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.home.library.data.local.entity.BookEntity
import com.home.library.data.local.enums.UserRole
import com.home.library.data.repository.BookRepository
import com.home.library.data.repository.DiscardResult
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
class BookDetailViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val bookId: Long = savedStateHandle[Routes.BOOK_DETAIL_ARG_ID] ?: Routes.BOOK_EDIT_NEW_ID
    private val _event = MutableStateFlow<DetailEvent?>(null)

    val ui: StateFlow<BookDetailUiState> = combine(
        bookRepository.flowById(bookId),
        sessionManager.state,
        _event,
    ) { book, session, event ->
        BookDetailUiState(
            book = book,
            isAdmin = (session as? SessionState.LoggedIn)?.role == UserRole.ROLE_ADMIN,
            event = event,
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
)

sealed interface DetailEvent {
    data object Deleted : DetailEvent
    data object HasActiveLoans : DetailEvent
}
