package com.home.library.data.local.view

import com.home.library.data.local.enums.LoanStatus

/** 관리자 전체 대출 현황(활성 대출 + 대출자명). SCR-13 / HIST-003. */
data class AdminLoanView(
    val loanId: Long,
    val bookTitle: String,
    val borrowerName: String,
    val dueDate: Long,
    val status: LoanStatus,
)
