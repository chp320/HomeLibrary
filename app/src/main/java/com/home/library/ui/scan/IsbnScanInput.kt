package com.home.library.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.home.library.R
import com.home.library.book.BookFormValidator

/**
 * 바코드/ISBN 입력 공용 컴포넌트 (SCR-06 재사용).
 * HID 스캐너·수동 입력·카메라 3소스를 수렴해 정규화+13자리+체크디지트 통과분만 [onIsbn]으로 넘긴다.
 * 이후 처리(로컬조회/API/대출 등)는 각 화면이 담당한다.
 */
@Composable
fun IsbnScanInput(
    onIsbn: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var cameraActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) cameraActive = true }

    fun dispatch(raw: String) {
        val normalized = BookFormValidator.normalizeIsbn(raw)
        if (normalized == null || BookFormValidator.validateIsbn(raw) != null) {
            error = true
            return
        }
        input = ""
        error = false
        cameraActive = false
        onIsbn(normalized)
    }

    // 진입/재진입 시 재포커스 → HID 스캐너/연속 입력
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Number로 제한하지 않아 하이픈(978-89-...) 허용
        OutlinedTextField(
            value = input,
            onValueChange = { v ->
                if (v.contains('\n') || v.contains('\r')) {
                    dispatch(v.replace("\n", "").replace("\r", ""))
                } else {
                    input = v
                    error = false
                }
            },
            label = { Text(stringResource(R.string.scan_field_isbn)) },
            singleLine = true,
            isError = error,
            supportingText = if (error) {
                { Text(stringResource(R.string.scan_error_invalid_isbn)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { dispatch(input) }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )

        Button(
            onClick = { dispatch(input) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.scan_lookup)) }

        OutlinedButton(
            onClick = {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) cameraActive = true else cameraPermission.launch(Manifest.permission.CAMERA)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.scan_use_camera)) }

        if (cameraActive) {
            CameraScanner(
                onIsbn = { dispatch(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
            )
            TextButton(
                onClick = { cameraActive = false },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.scan_close_camera)) }
        }
    }
}
