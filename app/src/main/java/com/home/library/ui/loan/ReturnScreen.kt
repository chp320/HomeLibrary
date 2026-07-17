package com.home.library.ui.loan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.home.library.R
import com.home.library.ui.common.BackButton
import com.home.library.ui.common.HomeButton
import com.home.library.data.local.enums.LoanStatus
import com.home.library.data.local.view.ActiveLoanView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReturnScreen(
    onNavigateLogin: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReturnViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()

    // 반납 확인 다이얼로그(A-6). null이 아니면 그 대출 건의 확인을 띄운다.
    var confirmTarget by remember { mutableStateOf<ActiveLoanView?>(null) }
    // 일괄 반납 확인 다이얼로그(A-11).
    var confirmBatch by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 일괄 반납 결과 요약. 실패 건은 목록에 그대로 남으므로 스낵바로 알리고 목록으로 확인하게 한다.
    val summary = state.summary
    val successMessage = summary?.let { stringResource(R.string.return_batch_result_success, it.success) }
    val partialMessage = summary?.let {
        stringResource(R.string.return_batch_result_partial, it.success, it.failure)
    }
    LaunchedEffect(summary) {
        if (summary != null) {
            snackbarHostState.showSnackbar(
                if (summary.failure == 0) successMessage!! else partialMessage!!,
            )
            viewModel.onSummaryShown()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.return_title)) },
                navigationIcon = {
                    BackButton(onBack)
                },
                actions = {
                    HomeButton(onHome)
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
            when {
                !state.isLoggedIn -> {
                    Text(stringResource(R.string.return_login_required))
                    Button(onClick = onNavigateLogin, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.common_login))
                    }
                }

                state.loans.isEmpty() -> {
                    Text(stringResource(R.string.return_empty))
                }

                else -> {
                    // 전체 선택 + 선택 반납(A-11).
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = state.allSelected,
                            onCheckedChange = { viewModel.toggleAll() },
                            enabled = !state.processing,
                        )
                        Text(
                            text = stringResource(R.string.return_select_all),
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { confirmBatch = true },
                            enabled = state.selected.isNotEmpty() && !state.processing,
                        ) {
                            Text(stringResource(R.string.return_batch_button, state.selected.size))
                        }
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.loans, key = { it.loanId }) { loan ->
                            ReturnRow(
                                loan = loan,
                                selected = loan.loanId in state.selected,
                                enabled = !state.processing,
                                onToggle = { viewModel.toggle(loan.loanId) },
                                onReturn = { confirmTarget = loan },
                            )
                        }
                    }
                }
            }
        }
    }

    // A-6: 반납은 되돌리기 번거로우므로 확인을 받는다.
    confirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmTarget = null },
            title = { Text(stringResource(R.string.return_confirm_title)) },
            text = { Text(stringResource(R.string.return_confirm_message, target.bookTitle)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.returnBook(target.loanId)
                    confirmTarget = null
                }) { Text(stringResource(R.string.return_button)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    // A-11: 일괄 반납 확인.
    if (confirmBatch) {
        AlertDialog(
            onDismissRequest = { confirmBatch = false },
            title = { Text(stringResource(R.string.return_confirm_title)) },
            text = {
                Text(stringResource(R.string.return_batch_confirm_message, state.selected.size))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.returnSelected()
                    confirmBatch = false
                }) { Text(stringResource(R.string.return_button)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatch = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun ReturnRow(
    loan: ActiveLoanView,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onReturn: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val overdue = loan.status == LoanStatus.OVERDUE || loan.dueDate < now
    // 체크박스가 여러 개라 스크린리더가 어느 도서인지 구분할 수 있게 도서명을 붙인다.
    val selectLabel = stringResource(R.string.return_select_item, loan.bookTitle)

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = selectLabel },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(loan.bookTitle, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.return_due, formatDate(loan.dueDate)),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = remainingText(loan.dueDate, now, overdue),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            Button(onClick = onReturn) { Text(stringResource(R.string.return_button)) }
        }
    }
}
