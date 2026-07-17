package com.home.library.ui.loan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.home.library.data.local.view.ActiveLoanView
import com.home.library.data.repository.LoanRepository
import com.home.library.data.repository.ReturnResult
import com.home.library.session.SessionManager
import com.home.library.session.SessionState
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReturnViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    /** 활성 대출 목록. null이면 비로그인. 세션은 userId만 뽑아 터치마다 재조회되지 않게 한다. */
    private val loansFlow: Flow<List<ActiveLoanView>?> = sessionManager.state
        .map { (it as? SessionState.LoggedIn)?.userId }
        .distinctUntilChanged()
        .flatMapLatest { userId ->
            if (userId == null) flowOf(null) else loanRepository.activeLoans(userId)
        }

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    private val _processing = MutableStateFlow(false)
    private val _summary = MutableStateFlow<ReturnSummary?>(null)

    val ui: StateFlow<ReturnUiState> = combine(
        loansFlow,
        _selected,
        _processing,
        _summary,
    ) { loans, selected, processing, summary ->
        val list = loans ?: emptyList()
        ReturnUiState(
            isLoggedIn = loans != null,
            loans = list,
            // 반납되어 목록에서 사라진 건은 선택에서 자동 해제된다.
            selected = selected intersect list.map { it.loanId }.toSet(),
            processing = processing,
            summary = summary,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ReturnUiState())

    fun toggle(loanId: Long) = _selected.update { current ->
        if (loanId in current) current - loanId else current + loanId
    }

    /** 전체 선택/해제. 이미 전부 선택된 상태면 해제로 동작한다. */
    fun toggleAll() {
        val all = ui.value.loans.map { it.loanId }.toSet()
        _selected.value = if (all.isNotEmpty() && ui.value.selected.size == all.size) emptySet() else all
    }

    fun returnBook(loanId: Long) {
        val session = sessionManager.state.value as? SessionState.LoggedIn ?: return
        viewModelScope.launch {
            // 트랜잭션 동안 자동 로그아웃 유예. 목록은 Flow라 반납 후 자동 갱신.
            sessionManager.withCriticalSection {
                loanRepository.returnBook(loanId, session.userId)
            }
        }
    }

    /**
     * 선택 건 일괄 반납(A-11).
     *
     * 각 반납은 [LoanRepository.returnBooks] 안에서 **개별 트랜잭션**으로 처리된다 —
     * 1건이 실패해도 나머지는 커밋된다.
     * 크리티컬 섹션(자동 로그아웃 유예)은 루프 전체를 **한 번만** 감싼다.
     * 건별로 감싸면 유예 카운터가 반복 증감할 뿐 이득이 없다.
     */
    fun returnSelected() {
        val session = sessionManager.state.value as? SessionState.LoggedIn ?: return
        val ids = ui.value.selected.toList()
        if (ids.isEmpty() || _processing.value) return
        viewModelScope.launch {
            _processing.value = true
            try {
                val outcomes = sessionManager.withCriticalSection {
                    loanRepository.returnBooks(ids, session.userId)
                }
                val success = outcomes.count { it.result == ReturnResult.Success }
                _selected.value = emptySet()
                _summary.value = ReturnSummary(success = success, failure = outcomes.size - success)
            } finally {
                _processing.value = false
            }
        }
    }

    /** 결과 요약을 화면이 표시한 뒤 호출. */
    fun onSummaryShown() { _summary.value = null }
}

data class ReturnUiState(
    val isLoggedIn: Boolean = false,
    val loans: List<ActiveLoanView> = emptyList(),
    /** 일괄 반납 선택 건. 목록에 없는 id는 걸러진 상태다. */
    val selected: Set<Long> = emptySet(),
    val processing: Boolean = false,
    val summary: ReturnSummary? = null,
) {
    val allSelected: Boolean get() = loans.isNotEmpty() && selected.size == loans.size
}

/** 일괄 반납 결과 요약("2건 성공, 1건 실패"). */
data class ReturnSummary(val success: Int, val failure: Int)
