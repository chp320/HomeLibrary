package com.home.library.ui.navigation

/** 네비게이션 경로 상수. 문자열 하드코딩 방지. */
object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val HOME = "home"

    const val CHANGE_PASSWORD_ARG_USER_ID = "userId"
    const val CHANGE_PASSWORD = "changePassword/{$CHANGE_PASSWORD_ARG_USER_ID}"

    fun changePassword(userId: Long): String = "changePassword/$userId"
}
