package com.home.library.ui.loan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
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

    /**
     * DateRangePicker가 넘긴 UTC 자정 millis를 받아 사용자가 고른 날짜(LocalDate)로 저장한다.
     * null이면 기간 미선택(전체). 선택 즉시 재조회.
     */
    fun setDateRange(startUtcMillis: Long?, endUtcMillis: Long?) {
        val start = startUtcMillis?.toPickedLocalDate()
        val end = endUtcMillis?.toPickedLocalDate()
        _history.update { it.copy(startDate = start, endDate = end) }
        applyFilters()
    }

    fun clearDateRange() {
        _history.update { it.copy(startDate = null, endDate = null) }
        applyFilters()
    }

    /** 필터(도서명/기간) 적용 → 첫 페이지부터 새로 로드. */
    fun applyFilters() {
        offset = 0
        _history.update { it.copy(records = emptyList(), canLoadMore = true) }
        loadMore()
    }

    fun loadMore() {
        val userId = (sessionManager.state.value as? SessionState.LoggedIn)?.userId ?: return
        val s = _history.value
        if (s.loading || !s.canLoadMore) return
        // 하루 경계는 반드시 시스템 시간대(KST 등)로 변환. 종료일은 그날 24:00-1ms까지 포함(inclusive).
        val from = s.startDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: 0L
        val to = s.endDate?.plusDays(1)?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
            ?.minus(1) ?: Long.MAX_VALUE
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

    /** 피커의 UTC 자정 millis → 고른 날짜. UTC로 해석해야 사용자가 탭한 날짜가 나온다. */
    private fun Long.toPickedLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

    companion object {
        private const val PAGE_SIZE = 20
    }
}

data class HistoryUiState(
    val bookQuery: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val records: List<LoanHistoryRecord> = emptyList(),
    val canLoadMore: Boolean = true,
    val loading: Boolean = false,
)
