package com.home.library.ui.scan

import androidx.lifecycle.ViewModel
import com.home.library.book.BookFormValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * 세 입력 소스(HID 스캐너·수동 키보드·카메라)를 동일한 [dispatch]로 수렴시킨다.
 * 정규화·13자리+체크디지트 검증을 통과한 ISBN만 dispatchedIsbn 이벤트로 방출한다.
 */
@HiltViewModel
class ScanViewModel @Inject constructor() : ViewModel() {

    private val _ui = MutableStateFlow(ScanUiState())
    val ui: StateFlow<ScanUiState> = _ui.asStateFlow()

    fun onInputChange(v: String) {
        // 스캐너가 개행(Enter)을 붙이면 즉시 조회(Enter 접미사 대비)
        if (v.contains('\n') || v.contains('\r')) {
            dispatch(v.replace("\n", "").replace("\r", ""))
        } else {
            _ui.update { it.copy(input = v, error = false) }
        }
    }

    /** 조회 버튼 / IME Done. */
    fun onSubmit() = dispatch(_ui.value.input)

    /** 카메라 인식 콜백. */
    fun onCameraCaptured(raw: String) = dispatch(raw)

    private fun dispatch(raw: String) {
        val normalized = BookFormValidator.normalizeIsbn(raw)
        if (normalized == null || BookFormValidator.validateIsbn(raw) != null) {
            _ui.update { it.copy(error = true) }
            return
        }
        // 성공: 입력 비우고(연속 스캔) ISBN 이벤트 방출, 카메라는 닫음
        _ui.update {
            it.copy(input = "", error = false, cameraActive = false, dispatchedIsbn = normalized)
        }
    }

    fun onIsbnConsumed() = _ui.update { it.copy(dispatchedIsbn = null) }

    fun setCameraActive(active: Boolean) = _ui.update { it.copy(cameraActive = active, error = false) }
}

data class ScanUiState(
    val input: String = "",
    val error: Boolean = false,
    val cameraActive: Boolean = false,
    /** 값이 있으면 해당 ISBN으로 등록 화면 이동(1회성). */
    val dispatchedIsbn: String? = null,
)
