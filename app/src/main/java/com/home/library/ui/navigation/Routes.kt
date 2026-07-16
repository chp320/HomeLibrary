package com.home.library.ui.navigation

/** 네비게이션 경로 상수. 문자열 하드코딩 방지. */
object Routes {
    /** 앱 메인화면 = 도서 목록(로그인 불필요, start destination). */
    const val BOOK_LIST = "bookList"

    const val LOGIN = "login"
    const val SIGNUP = "signup"

    const val BOOK_DETAIL_ARG_ID = "bookId"
    const val BOOK_DETAIL = "bookDetail/{$BOOK_DETAIL_ARG_ID}"
    fun bookDetail(bookId: Long): String = "bookDetail/$bookId"

    /** 등록/수정 공용 폼. bookId 미지정(-1)=등록, 지정=수정. */
    const val BOOK_EDIT_ARG_ID = "bookId"
    const val BOOK_EDIT_NEW_ID = -1L
    const val BOOK_EDIT = "bookEdit?bookId={$BOOK_EDIT_ARG_ID}"
    const val BOOK_EDIT_PREFIX = "bookEdit"
    fun bookEdit(bookId: Long = BOOK_EDIT_NEW_ID): String = "bookEdit?bookId=$bookId"

    const val CHANGE_PASSWORD_ARG_USER_ID = "userId"
    const val CHANGE_PASSWORD = "changePassword/{$CHANGE_PASSWORD_ARG_USER_ID}"
    fun changePassword(userId: Long): String = "changePassword/$userId"
}
