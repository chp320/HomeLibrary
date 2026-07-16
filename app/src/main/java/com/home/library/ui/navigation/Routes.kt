package com.home.library.ui.navigation

/** 네비게이션 경로 상수. 문자열 하드코딩 방지. */
object Routes {
    /** 앱 메인화면 = 도서 목록(로그인 불필요, start destination). */
    const val BOOK_LIST = "bookList"

    const val LOGIN = "login"
    const val SIGNUP = "signup"

    /** 바코드 스캔(SCR-06). 관리자 전용 진입. */
    const val SCAN = "scan"

    const val BOOK_DETAIL_ARG_ID = "bookId"
    const val BOOK_DETAIL = "bookDetail/{$BOOK_DETAIL_ARG_ID}"
    fun bookDetail(bookId: Long): String = "bookDetail/$bookId"

    /**
     * 등록/수정 공용 폼.
     * - bookId 미지정(-1)=등록, 지정=수정.
     * - isbn 지정=스캔 경로(로컬조회→API 자동채움).
     */
    const val BOOK_EDIT_ARG_ID = "bookId"
    const val BOOK_EDIT_ARG_ISBN = "isbn"
    const val BOOK_EDIT_NEW_ID = -1L
    const val BOOK_EDIT = "bookEdit?bookId={$BOOK_EDIT_ARG_ID}&isbn={$BOOK_EDIT_ARG_ISBN}"
    const val BOOK_EDIT_PREFIX = "bookEdit"
    fun bookEdit(bookId: Long = BOOK_EDIT_NEW_ID, isbn: String? = null): String =
        "bookEdit?bookId=$bookId&isbn=${isbn.orEmpty()}"

    const val CHANGE_PASSWORD_ARG_USER_ID = "userId"
    const val CHANGE_PASSWORD = "changePassword/{$CHANGE_PASSWORD_ARG_USER_ID}"
    fun changePassword(userId: Long): String = "changePassword/$userId"

    /** 대출(SCR-07). bookId 미지정=스캔으로 선택, 지정=해당 도서 대출. 로그인 필요. */
    const val LOAN_ARG_BOOK_ID = "bookId"
    const val LOAN_NO_BOOK_ID = -1L
    const val LOAN = "loan?bookId={$LOAN_ARG_BOOK_ID}"
    const val LOAN_PREFIX = "loan"
    fun loan(bookId: Long = LOAN_NO_BOOK_ID): String = "loan?bookId=$bookId"

    /** 반납(SCR-08). 로그인 필요. */
    const val RETURN = "return"

    /** 내 대출(현황+이력, SCR-09). 로그인 필요. */
    const val MY_LOAN = "myLoan"

    /** 관리자 홈(SCR-10). 관리자 전용. */
    const val ADMIN_HOME = "adminHome"

    /** 관리자 전체 대출 현황(SCR-13). 관리자 전용. */
    const val ADMIN_LOAN_STATUS = "adminLoanStatus"

    /** 사용자 관리 목록(SCR-12). 관리자 전용. */
    const val USER_LIST = "userList"

    /** 사용자 등록/수정. userId 미지정(-1)=등록, 지정=수정. 관리자 전용. */
    const val USER_EDIT_ARG_ID = "userId"
    const val USER_EDIT_NEW_ID = -1L
    const val USER_EDIT = "userEdit?userId={$USER_EDIT_ARG_ID}"
    const val USER_EDIT_PREFIX = "userEdit"
    fun userEdit(userId: Long = USER_EDIT_NEW_ID): String = "userEdit?userId=$userId"
}
