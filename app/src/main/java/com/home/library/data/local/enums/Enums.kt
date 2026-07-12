package com.home.library.data.local.enums

/** 사용자 역할. enum은 name(String)으로 저장한다 (설계 원칙 1). */
enum class UserRole {
    ROLE_ADMIN,
    ROLE_USER,
}

/** 사용자 상태. 물리 삭제 금지 → INACTIVE로 논리 삭제 (설계 원칙 2). */
enum class UserStatus {
    ACTIVE,
    INACTIVE,
    LOCKED,
}

/** 도서 상태. 물리 삭제 금지 → DISCARDED로 논리 삭제 (설계 원칙 2). */
enum class BookStatus {
    AVAILABLE,
    LOST,
    DISCARDED,
}

/** 대출 상태. */
enum class LoanStatus {
    LOANED,
    RETURNED,
    OVERDUE,
}

/** 이력 행위. LOAN_HISTORY는 append-only. */
enum class LoanAction {
    LOAN,
    RETURN,
    EXTEND,
    FORCE_RETURN,
}
