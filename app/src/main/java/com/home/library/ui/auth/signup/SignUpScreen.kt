package com.home.library.ui.auth.signup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.home.library.R
import com.home.library.auth.LoginIdError
import com.home.library.auth.NameError
import com.home.library.auth.PasswordError

@Composable
fun SignUpScreen(
    onSignedUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()

    LaunchedEffect(state.done) {
        if (state.done) onSignedUp()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.signup_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        Field(
            value = state.loginId,
            onValueChange = viewModel::onLoginIdChange,
            label = stringResource(R.string.signup_field_login_id),
            error = loginIdErrorMessage(state.loginIdError, state.loginIdDuplicate),
        )

        Field(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.signup_field_password),
            error = passwordErrorMessage(state.passwordError),
            isPassword = true,
        )

        Field(
            value = state.passwordConfirm,
            onValueChange = viewModel::onPasswordConfirmChange,
            label = stringResource(R.string.signup_field_password_confirm),
            error = if (state.confirmMismatch) stringResource(R.string.signup_error_password_mismatch) else null,
            isPassword = true,
        )

        Field(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = stringResource(R.string.signup_field_name),
            error = nameErrorMessage(state.nameError),
        )

        Field(
            value = state.phone,
            onValueChange = viewModel::onPhoneChange,
            label = stringResource(R.string.signup_field_phone),
            error = null,
            keyboardType = KeyboardType.Phone,
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
                Text(stringResource(R.string.signup_button))
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val supporting: (@Composable () -> Unit)? = error?.let { msg -> { Text(msg) } }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
        ),
        supportingText = supporting,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun loginIdErrorMessage(error: LoginIdError?, duplicate: Boolean): String? = when {
    duplicate -> stringResource(R.string.signup_error_login_id_duplicate)
    error == LoginIdError.BLANK -> stringResource(R.string.signup_error_login_id_blank)
    error == LoginIdError.LENGTH -> stringResource(R.string.signup_error_login_id_length)
    error == LoginIdError.FORMAT -> stringResource(R.string.signup_error_login_id_format)
    else -> null
}

@Composable
private fun passwordErrorMessage(error: PasswordError?): String? = when (error) {
    PasswordError.BLANK -> stringResource(R.string.signup_error_password_blank)
    PasswordError.LENGTH -> stringResource(R.string.signup_error_password_length)
    PasswordError.COMPOSITION -> stringResource(R.string.signup_error_password_composition)
    null -> null
}

@Composable
private fun nameErrorMessage(error: NameError?): String? = when (error) {
    NameError.BLANK -> stringResource(R.string.signup_error_name_blank)
    NameError.LENGTH -> stringResource(R.string.signup_error_name_length)
    null -> null
}
