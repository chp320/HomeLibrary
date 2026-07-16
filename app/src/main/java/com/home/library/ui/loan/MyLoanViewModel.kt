package com.home.library.ui.loan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.home.library.book.BookFormValidator
import com.home.library.data.local.view.ActiveLoanView
import com.home.library.data.local.view.LoanHistoryRecord
import com.home.library.data.repository.LoanRepository
import com.home.library.session.SessionManager
import com.home.library.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyLoanViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = sessionManager.state
        .map { it is SessionState.LoggedIn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), false)

    /** 현황 탭: 활성 대출(Flow 자동 갱신). */
    val active: StateFlow<List<ActiveLoanView>> = sessionManager.state
        .flatMapLatest { s ->
            val u = s as? SessionState.LoggedIn
            if (u == null) flowOf(emptyList()) else loanRepository.activeLoans(u.userId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private val _history = MutableStateFlow(HistoryUiState())
    val history: StateFlow<HistoryUiState> = _history.asStateFlow()

    private var offset = 0

    init {
        applyFilters()
    }

    fun onBookQueryChange(v: String) = _history.update { it.copy(bookQuery = v) }
    fun onFromDateChange(v: String) = _history.update { it.copy(fromDate = v, dateError = false) }
    fun onToDateChange(v: String) = _history.update { it.copy(toDate = v, dateError = false) }

    /** 필터 적용(첫 페이지부터 새로 로드). */
    fun applyFilters() {
        val s = _history.value
        if (BookFormValidator.validatePubDate(s.fromDate) != null ||
            BookFormValidator.validatePubDate(s.toDate) != null
        ) {
            _history.update { it.copy(dateError = true) }
            return
        }
        offset = 0
        _history.update { it.copy(records = emptyList(), canLoadMore = true, dateError = false) }
        loadMore()
    }

    fun loadMore() {
        val userId = (sessionManager.state.value as? SessionState.LoggedIn)?.userId ?: return
        val s = _history.value
        if (s.loading || !s.canLoadMore) return
        val from = s.fromDate.toStartMillis() ?: 0L
        val to = s.toDate.toEndMillis() ?: Long.MAX_VALUE
        viewModelScope.launch {
            _history.update { it.copy(loading = true) }
            val page = loanRepository.userLoanHistory(userId, s.bookQuery, from, to, PAGE_SIZE, offset)
            offset += page.size
            _history.update {
                it.copy(
                    records = it.records + page,
                    canLoadMore = page.size == PAGE_SIZE,
                    loading = false,
                )
            }
        }
    }

    private fun MutableStateFlow<HistoryUiState>.update(f: (HistoryUiState) -> HistoryUiState) {
        value = f(value)
    }

    private fun String.toStartMillis(): Long? =
        trim().ifBlank { null }?.let {
            LocalDate.parse(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

    private fun String.toEndMillis(): Long? =
        trim().ifBlank { null }?.let {
            LocalDate.parse(it).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        }

    companion object {
        private const val PAGE_SIZE = 20
    }
}

data class HistoryUiState(
    val bookQuery: String = "",
    val fromDate: String = "",
    val toDate: String = "",
    val dateError: Boolean = false,
    val records: List<LoanHistoryRecord> = emptyList(),
    val canLoadMore: Boolean = true,
    val loading: Boolean = false,
)
