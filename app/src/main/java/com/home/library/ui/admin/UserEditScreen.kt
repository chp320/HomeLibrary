package com.home.library.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.home.library.ui.common.BackButton
import com.home.library.ui.common.HomeButton
import com.home.library.auth.LoginIdError
import com.home.library.auth.NameError
import com.home.library.auth.PasswordError
import com.home.library.data.local.enums.UserRole
import com.home.library.data.local.enums.UserStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserEditScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserEditViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()

    LaunchedEffect(state.done) { if (state.done) onDone() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isEdit) R.string.user_edit_title_edit else R.string.user_edit_title_new))
                },
                navigationIcon = {
                    BackButton(onBack)
                },
                actions = {
                    HomeButton(onHome)
                },
            )
        },
    ) { innerPadding ->
        if (!state.isAdmin) {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                Text(stringResource(R.string.admin_no_permission))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Field(
                value = state.loginId,
                onValueChange = viewModel::onLoginIdChange,
                label = stringResource(R.string.user_field_login_id),
                error = loginIdError(state.loginIdError, state.loginIdDuplicate),
                enabled = !state.isEdit, // 수정 시 아이디 불변
            )
            Field(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = stringResource(if (state.isEdit) R.string.user_field_password_edit else R.string.user_field_password),
                error = passwordError(state.passwordError),
                isPassword = true,
            )
            Field(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = stringResource(R.string.user_field_name),
                error = nameError(state.nameError),
            )
            Field(
                value = state.phone,
                onValueChange = viewModel::onPhoneChange,
                label = stringResource(R.string.user_field_phone),
                error = null,
                keyboardType = KeyboardType.Phone,
            )

            Text(stringResource(R.string.user_field_role), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.role == UserRole.ROLE_USER, onClick = { viewModel.onRoleChange(UserRole.ROLE_USER) },
                    label = { Text(stringResource(R.string.role_user)) })
                FilterChip(selected = state.role == UserRole.ROLE_ADMIN, onClick = { viewModel.onRoleChange(UserRole.ROLE_ADMIN) },
                    label = { Text(stringResource(R.string.role_admin)) })
            }

            if (state.isEdit) {
                Text(stringResource(R.string.user_field_status), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.status == UserStatus.ACTIVE, onClick = { viewModel.onStatusChange(UserStatus.ACTIVE) },
                        label = { Text(stringResource(R.string.status_active)) })
                    FilterChip(selected = state.status == UserStatus.INACTIVE, onClick = { viewModel.onStatusChange(UserStatus.INACTIVE) },
                        label = { Text(stringResource(R.string.status_inactive)) })
                }
                if (state.hasActiveLoans) {
                    Text(
                        text = stringResource(R.string.user_error_has_active_loans),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Button(onClick = viewModel::submit, enabled = !state.loading, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                if (state.loading) CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                else Text(stringResource(R.string.user_save))
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
    enabled: Boolean = true,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val supporting: (@Composable () -> Unit)? = error?.let { msg -> { Text(msg) } }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType),
        supportingText = supporting,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun loginIdError(error: LoginIdError?, duplicate: Boolean): String? = when {
    duplicate -> stringResource(R.string.user_error_login_id_duplicate)
    error == LoginIdError.BLANK -> stringResource(R.string.signup_error_login_id_blank)
    error == LoginIdError.LENGTH -> stringResource(R.string.signup_error_login_id_length)
    error == LoginIdError.FORMAT -> stringResource(R.string.signup_error_login_id_format)
    else -> null
}

@Composable
private fun passwordError(error: PasswordError?): String? = when (error) {
    PasswordError.BLANK -> stringResource(R.string.signup_error_password_blank)
    PasswordError.LENGTH -> stringResource(R.string.signup_error_password_length)
    PasswordError.COMPOSITION -> stringResource(R.string.signup_error_password_composition)
    null -> null
}

@Composable
private fun nameError(error: NameError?): String? = when (error) {
    NameError.BLANK -> stringResource(R.string.signup_error_name_blank)
    NameError.LENGTH -> stringResource(R.string.signup_error_name_length)
    null -> null
}
