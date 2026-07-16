package com.home.library.ui.loan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.home.library.R
import com.home.library.ui.scan.IsbnScanInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanScreen(
    onNavigateLogin: () -> Unit,
    onRegister: (String) -> Unit,
    onLoaned: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoanViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()

    LaunchedEffect(state.success) {
        if (state.success) onLoaned()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.loan_title)) },
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
            when {
                !state.isLoggedIn -> {
                    Text(stringResource(R.string.loan_login_required))
                    Button(onClick = onNavigateLogin, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.common_login))
                    }
                }

                state.book == null -> {
                    Text(
                        text = stringResource(R.string.loan_scan_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    IsbnScanInput(onIsbn = viewModel::onIsbnScanned)
                    MessageArea(state, onRegister)
                }

                else -> {
                    val book = state.book!!
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(book.title, style = MaterialTheme.typography.titleMedium)
                            book.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            Text(
                                text = stringResource(R.string.book_available_count, book.availableQty, book.totalQty),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    state.borrowerName?.let {
                        Text(stringResource(R.string.loan_borrower, it))
                    }
                    MessageArea(state, onRegister)
                    Button(
                        onClick = viewModel::confirmLoan,
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                        } else {
                            Text(stringResource(R.string.loan_button))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageArea(state: LoanUiState, onRegister: (String) -> Unit) {
    val message = state.message ?: return
    Text(
        text = loanMessageText(message),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
    // 미등록 도서를 관리자면 등록 화면으로 유도
    if (message is LoanMessage.NotRegistered && state.isAdmin && state.lastIsbn != null) {
        OutlinedButton(
            onClick = { onRegister(state.lastIsbn) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.loan_register)) }
    }
}

@Composable
private fun loanMessageText(message: LoanMessage): String = when (message) {
    LoanMessage.NotRegistered -> stringResource(R.string.loan_msg_not_registered)
    LoanMessage.BookUnavailable -> stringResource(R.string.loan_msg_book_unavailable)
    LoanMessage.AlreadyBorrowed -> stringResource(R.string.loan_msg_already_borrowed)
    LoanMessage.HasOverdue -> stringResource(R.string.loan_msg_has_overdue)
    is LoanMessage.MaxCount -> stringResource(R.string.loan_msg_max_count, message.max)
    LoanMessage.NotAvailable -> stringResource(R.string.loan_msg_not_available)
}
