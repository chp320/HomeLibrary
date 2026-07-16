package com.home.library.data.local.view

import com.home.library.data.local.enums.LoanAction

/** 도서 상세의 해당 도서 대출 이력(append-only LOAN_HISTORY 표시). BOOK-008. */
data class BookLoanHistoryView(
    val action: LoanAction,
    val actionAt: Long,
    val actorName: String,
    val memo: String?,
)
