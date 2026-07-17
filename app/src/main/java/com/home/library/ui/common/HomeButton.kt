package com.home.library.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.home.library.R

/**
 * 서브 화면 공용 홈 버튼. 첫 화면(도서 목록)으로 바로 점프한다.
 *
 * Material 관례상 좌상단(navigationIcon)=계층 위로([BackButton]), 우상단(actions)=루트로 점프.
 * 인증 흐름(로그인/가입/강제 비밀번호 변경)에는 두지 않는다 —
 * 특히 강제 비밀번호 변경은 홈 점프로 변경을 우회하면 안 되므로 제외한다.
 */
@Composable
fun HomeButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Filled.Home,
            contentDescription = stringResource(R.string.common_home),
        )
    }
}
