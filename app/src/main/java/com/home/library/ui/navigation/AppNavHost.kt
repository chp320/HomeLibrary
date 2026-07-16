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
import com.home.library.ui.home.HomeScreen

/**
 * 최상위 네비게이션.
 * 로그인/로그아웃 경계는 SessionManager 상태로 구동한다:
 * - 로그인 성공(세션 시작) → HOME (백스택 초기화)
 * - 자동/수동 로그아웃(세션 종료) → LOGIN (백스택 초기화)
 *
 * 전환 판정은 "로그인 여부(boolean)"로만 트리거한다. 활동시각(lastActivityAt) 갱신처럼
 * 세션 내부 값이 바뀌는 경우에는 재이동이 발생하지 않도록 한다.
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
        if (isLoggedIn) {
            navController.navigate(Routes.HOME) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        } else {
            val current = navController.currentDestination?.route
            if (current != null && current != Routes.LOGIN) {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.LOGIN,
        modifier = modifier,
    ) {
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
        composable(Routes.HOME) {
            HomeScreen()
        }
    }
}
