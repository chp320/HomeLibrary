package com.home.library.ui.auth.changepw

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.home.library.R
import com.home.library.auth.PasswordError

@Composable
fun ChangePasswordScreen(
    modifier: Modifier = Modifier,
    viewModel: ChangePasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()

    // 강제 변경 화면: 뒤로가기로 우회 불가
    BackHandler(enabled = true) { /* 차단 */ }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.changepw_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.changepw_message),
            style = MaterialTheme.typography.bodyMedium,
        )

        val newPwError = newPasswordError(state.passwordError, state.sameAsOld)
        val newPwSupport: (@Composable () -> Unit)? = newPwError?.let { msg -> { Text(msg) } }
        OutlinedTextField(
            value = state.newPassword,
            onValueChange = viewModel::onNewPasswordChange,
            label = { Text(stringResource(R.string.changepw_field_new_password)) },
            singleLine = true,
            isError = state.passwordError != null || state.sameAsOld,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = newPwSupport,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.confirm,
            onValueChange = viewModel::onConfirmChange,
            label = { Text(stringResource(R.string.changepw_field_new_password_confirm)) },
            singleLine = true,
            isError = state.confirmMismatch,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = if (state.confirmMismatch) {
                { Text(stringResource(R.string.signup_error_password_mismatch)) }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = viewModel::submit,
            enabled = !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text(stringResource(R.string.changepw_button))
            }
        }
    }
}

@Composable
private fun newPasswordError(error: PasswordError?, sameAsOld: Boolean): String? = when {
    sameAsOld -> stringResource(R.string.changepw_error_same_as_old)
    error == PasswordError.BLANK -> stringResource(R.string.signup_error_password_blank)
    error == PasswordError.LENGTH -> stringResource(R.string.signup_error_password_length)
    error == PasswordError.COMPOSITION -> stringResource(R.string.signup_error_password_composition)
    else -> null
}
