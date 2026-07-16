package com.home.library.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.home.library.R
import com.home.library.ui.common.BackButton
import com.home.library.data.local.enums.UserRole
import com.home.library.data.local.enums.UserStatus
import com.home.library.data.local.view.UserListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    onAddUser: () -> Unit,
    onEditUser: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserListViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.user_list_title)) },
                navigationIcon = {
                    BackButton(onBack)
                },
            )
        },
        floatingActionButton = {
            if (state.isAdmin) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.user_add)) },
                    icon = { Text("+", style = MaterialTheme.typography.titleLarge) },
                    onClick = onAddUser,
                )
            }
        },
    ) { innerPadding ->
        if (!state.isAdmin) {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                Text(stringResource(R.string.admin_no_permission))
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text(stringResource(R.string.user_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                item {
                    FilterChip(selected = state.role == null, onClick = { viewModel.onRoleChange(null) },
                        label = { Text(stringResource(R.string.user_filter_all)) })
                }
                item {
                    FilterChip(selected = state.role == UserRole.ROLE_ADMIN, onClick = { viewModel.onRoleChange(UserRole.ROLE_ADMIN) },
                        label = { Text(stringResource(R.string.role_admin)) })
                }
                item {
                    FilterChip(selected = state.role == UserRole.ROLE_USER, onClick = { viewModel.onRoleChange(UserRole.ROLE_USER) },
                        label = { Text(stringResource(R.string.role_user)) })
                }
                item {
                    FilterChip(selected = state.status == UserStatus.ACTIVE, onClick = {
                        viewModel.onStatusChange(if (state.status == UserStatus.ACTIVE) null else UserStatus.ACTIVE)
                    }, label = { Text(stringResource(R.string.status_active)) })
                }
                item {
                    FilterChip(selected = state.status == UserStatus.INACTIVE, onClick = {
                        viewModel.onStatusChange(if (state.status == UserStatus.INACTIVE) null else UserStatus.INACTIVE)
                    }, label = { Text(stringResource(R.string.status_inactive)) })
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                items(state.users, key = { it.userId }) { user ->
                    UserRow(user, onClick = { onEditUser(user.userId) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserRow(user: UserListItem, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("${user.name} (${user.loginId})", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${roleLabel(user.role)} · ${statusLabel(user.status)} · " +
                    stringResource(R.string.user_active_count, user.activeCount),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun roleLabel(role: UserRole): String = when (role) {
    UserRole.ROLE_ADMIN -> stringResource(R.string.role_admin)
    UserRole.ROLE_USER -> stringResource(R.string.role_user)
}

@Composable
private fun statusLabel(status: UserStatus): String = when (status) {
    UserStatus.ACTIVE -> stringResource(R.string.status_active)
    UserStatus.INACTIVE -> stringResource(R.string.status_inactive)
    UserStatus.LOCKED -> stringResource(R.string.status_locked)
}
