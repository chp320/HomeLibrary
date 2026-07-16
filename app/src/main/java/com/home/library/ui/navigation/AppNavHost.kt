package com.home.library.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.home.library.session.SessionManager
import com.home.library.session.SessionState
import com.home.library.ui.auth.changepw.ChangePasswordScreen
import com.home.library.ui.auth.login.LoginScreen
import com.home.library.ui.auth.signup.SignUpScreen
import com.home.library.ui.admin.AdminHomeScreen
import com.home.library.ui.admin.AdminLoanStatusScreen
import com.home.library.ui.admin.UserEditScreen
import com.home.library.ui.admin.UserListScreen
import com.home.library.ui.book.detail.BookDetailScreen
import com.home.library.ui.book.edit.BookEditScreen
import com.home.library.ui.book.list.BookListScreen
import com.home.library.ui.loan.LoanScreen
import com.home.library.ui.loan.MyLoanScreen
import com.home.library.ui.loan.ReturnScreen
import com.home.library.ui.scan.ScanScreen

/**
 * 최상위 네비게이션.
 * 앱 메인화면 = 도서 목록(로그인 불필요). 로그인/로그아웃은 세션 상태로 보조 구동한다:
 * - 로그인 성공(세션 시작) 시 LOGIN/CHANGE_PASSWORD 위에 있으면 도서 목록으로 복귀.
 * - 로그아웃/세션 만료 시 로그인 필요 화면(도서 편집)에 있으면 도서 목록으로 복귀.
 *   (요구사항: 만료 시 로그인 화면이 아니라 도서 목록으로 돌아온다.)
 */
@Composable
fun AppNavHost(
    sessionManager: SessionManager,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val sessionState by sessionManager.state.collectAsState()
    val isLoggedIn = sessionState is SessionState.LoggedIn

    LaunchedEffect(isLoggedIn) {
        val current = navController.currentDestination?.route
        if (isLoggedIn) {
            // 로그인 성공 시 인증 화면만 pop → 호출한 화면(대출 등)으로 복귀
            if (current == Routes.LOGIN || current == Routes.CHANGE_PASSWORD) {
                navController.popBackStack(Routes.LOGIN, inclusive = true)
            }
        } else {
            // 로그아웃/만료 → 로그인 필요 화면(스캔/편집/대출/반납)이면 목록으로 복귀
            if (current != null && current.isLoginRequired()) {
                navController.popBackStack(Routes.BOOK_LIST, inclusive = false)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.BOOK_LIST,
        modifier = modifier,
    ) {
        composable(Routes.BOOK_LIST) {
            BookListScreen(
                onBookClick = { id -> navController.navigate(Routes.bookDetail(id)) },
                onAddBook = { navController.navigate(Routes.bookEdit()) },
                onNavigateScan = { navController.navigate(Routes.SCAN) },
                onNavigateLoan = { navController.navigate(Routes.loan()) },
                onNavigateReturn = { navController.navigate(Routes.RETURN) },
                onNavigateMyLoan = { navController.navigate(Routes.MY_LOAN) },
                onNavigateAdmin = { navController.navigate(Routes.ADMIN_HOME) },
                onNavigateLogin = { navController.navigate(Routes.LOGIN) },
            )
        }
        composable(Routes.MY_LOAN) {
            MyLoanScreen(
                onNavigateReturn = { navController.navigate(Routes.RETURN) },
                onNavigateLogin = { navController.navigate(Routes.LOGIN) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.ADMIN_HOME) {
            AdminHomeScreen(
                onNavigateUsers = { navController.navigate(Routes.USER_LIST) },
                onNavigateLoanStatus = { navController.navigate(Routes.ADMIN_LOAN_STATUS) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.ADMIN_LOAN_STATUS) {
            AdminLoanStatusScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.USER_LIST) {
            UserListScreen(
                onAddUser = { navController.navigate(Routes.userEdit()) },
                onEditUser = { id -> navController.navigate(Routes.userEdit(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.USER_EDIT,
            arguments = listOf(
                navArgument(Routes.USER_EDIT_ARG_ID) {
                    type = NavType.LongType
                    defaultValue = Routes.USER_EDIT_NEW_ID
                },
            ),
        ) {
            UserEditScreen(
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SCAN) {
            ScanScreen(
                onIsbn = { isbn ->
                    navController.navigate(Routes.bookEdit(isbn = isbn))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onNavigateSignUp = { navController.navigate(Routes.SIGNUP) },
                onForceChangePassword = { userId -> navController.navigate(Routes.changePassword(userId)) },
            )
        }
        composable(Routes.SIGNUP) {
            SignUpScreen(onSignedUp = { navController.popBackStack() })
        }
        composable(
            route = Routes.CHANGE_PASSWORD,
            arguments = listOf(
                navArgument(Routes.CHANGE_PASSWORD_ARG_USER_ID) { type = NavType.LongType },
            ),
        ) {
            ChangePasswordScreen()
        }
        composable(
            route = Routes.BOOK_DETAIL,
            arguments = listOf(
                navArgument(Routes.BOOK_DETAIL_ARG_ID) { type = NavType.LongType },
            ),
        ) {
            BookDetailScreen(
                onEdit = { id -> navController.navigate(Routes.bookEdit(id)) },
                onLoan = { id -> navController.navigate(Routes.loan(id)) },
                onDeleted = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.BOOK_EDIT,
            arguments = listOf(
                navArgument(Routes.BOOK_EDIT_ARG_ID) {
                    type = NavType.LongType
                    defaultValue = Routes.BOOK_EDIT_NEW_ID
                },
                navArgument(Routes.BOOK_EDIT_ARG_ISBN) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            BookEditScreen(
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.LOAN,
            arguments = listOf(
                navArgument(Routes.LOAN_ARG_BOOK_ID) {
                    type = NavType.LongType
                    defaultValue = Routes.LOAN_NO_BOOK_ID
                },
            ),
        ) {
            LoanScreen(
                onNavigateLogin = { navController.navigate(Routes.LOGIN) },
                onRegister = { isbn -> navController.navigate(Routes.bookEdit(isbn = isbn)) },
                onLoaned = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.RETURN) {
            ReturnScreen(
                onNavigateLogin = { navController.navigate(Routes.LOGIN) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** 로그인 필요 화면인지(로그아웃/만료 시 도서 목록으로 되돌릴 대상). */
private fun String.isLoginRequired(): Boolean =
    this == Routes.SCAN ||
        this == Routes.RETURN ||
        this == Routes.MY_LOAN ||
        this == Routes.ADMIN_HOME ||
        this == Routes.ADMIN_LOAN_STATUS ||
        this == Routes.USER_LIST ||
        startsWith(Routes.BOOK_EDIT_PREFIX) ||
        startsWith(Routes.LOAN_PREFIX) ||
        startsWith(Routes.USER_EDIT_PREFIX)
