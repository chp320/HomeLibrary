package com.home.library.data.local.view

import com.home.library.data.local.enums.LoanStatus

/** 내 대출 이력(대출 레코드 단위, 반납 완료 포함). HIST-002. */
data class LoanHistoryRecord(
    val loanId: Long,
    val bookTitle: String,
    val loanDate: Long,
    val dueDate: Long,
    val returnDate: Long?,
    val status: LoanStatus,
)
