package com.home.library.ui.book.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.home.library.book.BookFormValidator
import com.home.library.book.IsbnError
import com.home.library.book.OptionalTextError
import com.home.library.book.PubDateError
import com.home.library.book.QuantityError
import com.home.library.book.TitleError
import com.home.library.data.local.enums.UserRole
import com.home.library.data.repository.BookForm
import com.home.library.data.repository.BookRepository
import com.home.library.data.repository.UpdateResult
import com.home.library.session.SessionManager
import com.home.library.session.SessionState
import com.home.library.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookEditViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val bookId: Long = savedStateHandle[Routes.BOOK_EDIT_ARG_ID] ?: Routes.BOOK_EDIT_NEW_ID
    private val isEdit: Boolean = bookId != Routes.BOOK_EDIT_NEW_ID

    private val _ui = MutableStateFlow(BookEditUiState(isEdit = isEdit))
    val ui: StateFlow<BookEditUiState> = _ui.asStateFlow()

    init {
        if (isEdit) load()
    }

    private fun load() {
        viewModelScope.launch {
            bookRepository.getById(bookId)?.let { b ->
                _ui.update {
                    it.copy(
                        isbn = b.isbn.orEmpty(),
                        title = b.title,
                        author = b.author.orEmpty(),
                        publisher = b.publisher.orEmpty(),
                        pubDate = b.pubDate.orEmpty(),
                        category = b.category.orEmpty(),
                        location = b.location.orEmpty(),
                        quantity = b.totalQty.toString(),
                    )
                }
            }
        }
    }

    fun onIsbnChange(v: String) = _ui.update { it.copy(isbn = v, isbnError = null) }
    fun onTitleChange(v: String) = _ui.update { it.copy(title = v, titleError = null) }
    fun onAuthorChange(v: String) = _ui.update { it.copy(author = v, authorError = null) }
    fun onPublisherChange(v: String) = _ui.update { it.copy(publisher = v, publisherError = null) }
    fun onPubDateChange(v: String) = _ui.update { it.copy(pubDate = v, pubDateError = null) }
    fun onCategoryChange(v: String) = _ui.update { it.copy(category = v, categoryError = null) }
    fun onLocationChange(v: String) = _ui.update { it.copy(location = v, locationError = null) }
    fun onQuantityChange(v: String) = _ui.update { it.copy(quantity = v, quantityError = null, belowLoaned = null) }

    fun submit() {
        val s = _ui.value
        if (s.loading) return
        if (!isAdmin()) {
            _ui.update { it.copy(notAdmin = true) }
            return
        }

        val titleError = BookFormValidator.validateTitle(s.title)
        val quantityError = BookFormValidator.validateQuantity(s.quantity)
        val isbnError = BookFormValidator.validateIsbn(s.isbn)
        val pubDateError = BookFormValidator.validatePubDate(s.pubDate)
        val authorError = BookFormValidator.validateOptionalText(s.author)
        val publisherError = BookFormValidator.validateOptionalText(s.publisher)
        val categoryError = BookFormValidator.validateOptionalText(s.category)
        val locationError = BookFormValidator.validateOptionalText(s.location)
        val hasError = titleError != null || quantityError != null || isbnError != null ||
            pubDateError != null || authorError != null || publisherError != null ||
            categoryError != null || locationError != null
        if (hasError) {
            _ui.update {
                it.copy(
                    titleError = titleError,
                    quantityError = quantityError,
                    isbnError = isbnError,
                    pubDateError = pubDateError,
                    authorError = authorError,
                    publisherError = publisherError,
                    categoryError = categoryError,
                    locationError = locationError,
                )
            }
            return
        }
        val qty = BookFormValidator.parseQuantity(s.quantity) ?: return
        val form = BookForm(
            isbn = s.isbn,
            title = s.title,
            author = s.author,
            publisher = s.publisher,
            pubDate = s.pubDate,
            category = s.category,
            location = s.location,
            totalQty = qty,
        )

        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            if (isEdit) {
                when (val r = bookRepository.updateBook(bookId, form)) {
                    UpdateResult.Success, UpdateResult.NotFound ->
                        _ui.update { it.copy(loading = false, done = true) }
                    is UpdateResult.QuantityBelowLoaned ->
                        _ui.update { it.copy(loading = false, belowLoaned = r.loaned) }
                }
            } else {
                // ISBN이 blank면 findActiveByIsbn이 null을 반환 → 무조건 신규 등록
                val existing = bookRepository.findActiveByIsbn(form.isbn)
                if (existing != null) {
                    _ui.update {
                        it.copy(
                            loading = false,
                            duplicate = DuplicateInfo(existing.bookId, existing.title, qty),
                        )
                    }
                } else {
                    bookRepository.register(form)
                    _ui.update { it.copy(loading = false, done = true) }
                }
            }
        }
    }

    /** 중복 확인 다이얼로그에서 "추가" 선택 → 수량만 증가. */
    fun confirmAddQuantity() {
        val dup = _ui.value.duplicate ?: return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, duplicate = null) }
            bookRepository.addQuantity(dup.bookId, dup.addQty)
            _ui.update { it.copy(loading = false, done = true) }
        }
    }

    fun dismissDuplicate() = _ui.update { it.copy(duplicate = null) }

    private fun isAdmin(): Boolean =
        (sessionManager.state.value as? SessionState.LoggedIn)?.role == UserRole.ROLE_ADMIN
}

data class BookEditUiState(
    val isEdit: Boolean = false,
    val isbn: String = "",
    val title: String = "",
    val author: String = "",
    val publisher: String = "",
    val pubDate: String = "",
    val category: String = "",
    val location: String = "",
    val quantity: String = "1",
    val titleError: TitleError? = null,
    val quantityError: QuantityError? = null,
    val isbnError: IsbnError? = null,
    val pubDateError: PubDateError? = null,
    val authorError: OptionalTextError? = null,
    val publisherError: OptionalTextError? = null,
    val categoryError: OptionalTextError? = null,
    val locationError: OptionalTextError? = null,
    /** 수정 시 새 수량이 대출중 수량보다 작을 때, 그 대출중 수량. */
    val belowLoaned: Int? = null,
    val loading: Boolean = false,
    val notAdmin: Boolean = false,
    val duplicate: DuplicateInfo? = null,
    val done: Boolean = false,
)

data class DuplicateInfo(val bookId: Long, val title: String, val addQty: Int)
