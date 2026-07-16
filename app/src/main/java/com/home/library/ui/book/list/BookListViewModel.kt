package com.home.library.ui.book.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.home.library.data.local.entity.BookEntity
import com.home.library.data.local.enums.UserRole
import com.home.library.data.repository.BookRepository
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
class BookListViewModel @Inject constructor(
    bookRepository: BookRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _category = MutableStateFlow<String?>(null)
    private val _availableOnly = MutableStateFlow(false)

    fun onQueryChange(v: String) { _query.value = v }
    fun onCategoryChange(v: String?) { _category.value = v }
    fun onAvailableOnlyChange(v: Boolean) { _availableOnly.value = v }
    fun logout() = sessionManager.clear()

    private val filters = combine(_query, _category, _availableOnly) { q, c, a ->
        Filters(q, c, a)
    }

    // 입력 디바운스 300ms 후 검색. 결과는 Room Flow라 등록/삭제 시 자동 갱신.
    private val books = filters
        .debounce(300L)
        .flatMapLatest { f -> bookRepository.search(f.query, f.category, f.availableOnly) }

    val ui: StateFlow<BookListUiState> = combine(
        filters,
        books,
        bookRepository.categories(),
        sessionManager.state,
    ) { f, list, cats, session ->
        val loggedIn = session as? SessionState.LoggedIn
        BookListUiState(
            query = f.query,
            category = f.category,
            availableOnly = f.availableOnly,
            categories = cats,
            books = list,
            isLoggedIn = loggedIn != null,
            isAdmin = loggedIn?.role == UserRole.ROLE_ADMIN,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), BookListUiState())

    private data class Filters(val query: String, val category: String?, val availableOnly: Boolean)
}

data class BookListUiState(
    val query: String = "",
    val category: String? = null,
    val availableOnly: Boolean = false,
    val categories: List<String> = emptyList(),
    val books: List<BookEntity> = emptyList(),
    val isLoggedIn: Boolean = false,
    val isAdmin: Boolean = false,
)
