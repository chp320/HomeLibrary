package com.home.library

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.home.library.session.SessionManager
import com.home.library.session.SessionTimeoutHandler
import com.home.library.ui.navigation.AppNavHost
import com.home.library.ui.theme.HomeLibraryTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var sessionTimeoutHandler: SessionTimeoutHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeLibraryTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavHost(
                        sessionManager = sessionManager,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 포그라운드 동안 10초 폴링 감시
        sessionTimeoutHandler.start(lifecycleScope)
    }

    override fun onResume() {
        super.onResume()
        // 백그라운드 경과분을 즉시 반영(복귀 시 곧바로 만료 판정)
        lifecycleScope.launch { sessionTimeoutHandler.checkExpiry() }
    }

    override fun onStop() {
        sessionTimeoutHandler.stop()
        super.onStop()
    }

    /** 터치 입력마다 활동시각 갱신(로그인 상태에서만 반영). */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        sessionManager.recordActivity()
        return super.dispatchTouchEvent(ev)
    }

    /** 키 입력마다 활동시각 갱신. USB-C HID 바코드 스캐너 입력(4단계)도 여기서 활동으로 인정된다. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        sessionManager.recordActivity()
        return super.dispatchKeyEvent(event)
    }
}
