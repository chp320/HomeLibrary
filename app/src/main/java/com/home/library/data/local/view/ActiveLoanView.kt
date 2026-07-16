package com.home.library.data.local.view

import com.home.library.data.local.enums.LoanStatus

/**
 * 활성 대출 목록 표시용 프로젝션(loans ⨝ books).
 * 반납 화면과 6단계 내 대출현황(HIST-001)에서 공용.
 */
data class ActiveLoanView(
    val loanId: Long,
    val bookId: Long,
    val bookTitle: String,
    val dueDate: Long,
    val status: LoanStatus,
)
