package com.home.library.loan

/**
 * 사용자의 대출 여력(표시 전용). 도서 목록(A-2)과 대출 확인 화면(A-5)이 공유한다.
 *
 * 실제 대출 허용 여부를 판정하는 건 [LoanValidator]다. 이 계산은 그 판정과 **같은 규칙**을
 * 따라야 하며, 어긋나면 "대출 가능하다더니 막상 누르면 거부당하는" 거짓말이 된다.
 * 규칙을 바꿀 때는 [LoanValidator.validate]와 함께 고칠 것.
 */
data class LoanAllowance(
    val activeCount: Int,
    val overdueCount: Int,
    val maxCount: Int,
) {
    val hasOverdue: Boolean get() = overdueCount > 0

    /**
     * 실질 대출 가능 권수.
     * 연체를 1건이라도 보유하면 잔여 권수와 무관하게 0이다 —
     * [LoanValidator]의 검증 ②(HasOverdue)가 최대권수 검증(③)보다 먼저 걸리기 때문.
     */
    val availableToBorrow: Int
        get() = if (hasOverdue) 0 else (maxCount - activeCount).coerceAtLeast(0)
}
