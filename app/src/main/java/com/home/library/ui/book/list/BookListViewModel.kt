package com.home.library.ui.book.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.home.library.data.local.entity.BookEntity
import com.home.library.data.local.enums.UserRole
import com.home.library.data.repository.BookRepository
import com.home.library.data.repository.LoanRepository
import com.home.library.loan.LoanAllowance
import com.home.library.session.SessionManager
import com.home.library.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class BookListViewModel @Inject constructor(
    bookRepository: BookRepository,
    loanRepository: LoanRepository,
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

    /**
     * 세션에서 표시에 필요한 값만 뽑는다.
     * SessionManager는 터치마다 lastActivityAt이 갱신된 새 LoggedIn을 emit하므로,
     * 그대로 구독하면 터치할 때마다 아래 allowance 조회가 재실행된다. distinctUntilChanged로 끊는다.
     */
    private val sessionInfo: Flow<SessionInfo?> = sessionManager.state
        .map { s -> (s as? SessionState.LoggedIn)?.let { SessionInfo(it.userId, it.name, it.role) } }
        .distinctUntilChanged()

    /**
     * 로그인 사용자 정보 + 대출 여력. 세션·여력을 한 소스로 묶어 combine 인자 수를 5개로 유지한다.
     * allowance는 DB 조회라 한 박자 늦으므로 onStart로 null을 먼저 흘려
     * 로그인 여부(상단바 버튼)가 지연 없이 반영되게 한다.
     */
    private val userInfo: Flow<UserInfo?> = sessionInfo.flatMapLatest { info ->
        if (info == null) {
            flowOf(null)
        } else {
            loanRepository.allowance(info.userId)
                .map<LoanAllowance, LoanAllowance?> { it }
                .onStart { emit(null) }
                .map { allowance -> UserInfo(info.name, info.role, allowance) }
        }
    }

    val ui: StateFlow<BookListUiState> = combine(
        filters,
        books,
        bookRepository.categories(),
        bookRepository.totalCount(),
        userInfo,
    ) { f, list, cats, total, user ->
        BookListUiState(
            query = f.query,
            category = f.category,
            availableOnly = f.availableOnly,
            categories = cats,
            books = list,
            totalCount = total,
            isLoggedIn = user != null,
            isAdmin = user?.role == UserRole.ROLE_ADMIN,
            userName = user?.name,
            allowance = user?.allowance,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), BookListUiState())

    private data class Filters(val query: String, val category: String?, val availableOnly: Boolean)

    /** 세션에서 표시에 쓰는 값만. lastActivityAt을 제외해 터치마다 재계산되는 것을 막는다. */
    private data class SessionInfo(val userId: Long, val name: String, val role: UserRole)

    private data class UserInfo(val name: String, val role: UserRole, val allowance: LoanAllowance?)
}

data class BookListUiState(
    val query: String = "",
    val category: String? = null,
    val availableOnly: Boolean = false,
    val categories: List<String> = emptyList(),
    val books: List<BookEntity> = emptyList(),
    /** 보유 도서 총 건수(DISCARDED 제외). 검색·필터와 무관. */
    val totalCount: Int = 0,
    val isLoggedIn: Boolean = false,
    val isAdmin: Boolean = false,
    /** 로그인 사용자 이름. 비로그인이면 null(사용자 정보 줄 숨김). */
    val userName: String? = null,
    /** 대출 여력. 조회 전이면 null(건수 미표시). */
    val allowance: LoanAllowance? = null,
) {
    /** 검색어·분류·대출가능 중 하나라도 걸려 있으면 결과 건수를 함께 보여준다. */
    val isFiltered: Boolean get() = query.isNotBlank() || category != null || availableOnly

    /** 검색 결과 건수. search 쿼리에 LIMIT/OFFSET이 없어 size가 정확하다. */
    val resultCount: Int get() = books.size
}
