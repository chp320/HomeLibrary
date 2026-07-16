package com.home.library.ui.loan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.home.library.R
import com.home.library.data.local.enums.LoanStatus
import com.home.library.data.local.view.ActiveLoanView
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReturnScreen(
    onNavigateLogin: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReturnViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.return_title)) },
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
                    Text(stringResource(R.string.return_login_required))
                    Button(onClick = onNavigateLogin, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.common_login))
                    }
                }

                state.loans.isEmpty() -> {
                    Text(stringResource(R.string.return_empty))
                }

                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.loans, key = { it.loanId }) { loan ->
                            ReturnRow(loan = loan, onReturn = { viewModel.returnBook(loan.loanId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReturnRow(loan: ActiveLoanView, onReturn: () -> Unit) {
    val now = System.currentTimeMillis()
    val overdue = loan.status == LoanStatus.OVERDUE || loan.dueDate < now

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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

private const val DAY_MS = 24L * 60 * 60 * 1000

@Composable
private fun remainingText(dueDate: Long, now: Long, overdue: Boolean): String =
    if (overdue) {
        val days = ((now - dueDate) / DAY_MS).toInt().coerceAtLeast(0)
        stringResource(R.string.return_overdue, days)
    } else {
        val days = ((dueDate - now) / DAY_MS).toInt()
        if (days <= 0) stringResource(R.string.return_dday_today) else stringResource(R.string.return_dday, days)
    }

private fun formatDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
