package com.home.library.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.home.library.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onIsbn: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.setCameraActive(true) }

    // ISBN 확정 시 등록 화면으로 이동(입력은 VM에서 이미 비움)
    LaunchedEffect(state.dispatchedIsbn) {
        state.dispatchedIsbn?.let {
            onIsbn(it)
            viewModel.onIsbnConsumed()
        }
    }
    // 화면 진입/재진입(등록 후 복귀) 시 재포커스 → 연속 스캔
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.scan_hint),
                style = MaterialTheme.typography.bodyMedium,
            )

            // HID 스캐너 + 수동 입력 공용 필드. Number로 제한하지 않아 하이픈(978-89-...) 허용.
            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                label = { Text(stringResource(R.string.scan_field_isbn)) },
                singleLine = true,
                isError = state.error,
                supportingText = if (state.error) {
                    { Text(stringResource(R.string.scan_error_invalid_isbn)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )

            // Enter 접미사가 없는 스캐너/수동 입력 폴백
            Button(
                onClick = viewModel::onSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.scan_lookup)) }

            OutlinedButton(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        viewModel.setCameraActive(true)
                    } else {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.scan_use_camera)) }

            if (state.cameraActive) {
                CameraScanner(
                    onIsbn = viewModel::onCameraCaptured,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                )
                TextButton(
                    onClick = { viewModel.setCameraActive(false) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.scan_close_camera)) }
            }
        }
    }
}
