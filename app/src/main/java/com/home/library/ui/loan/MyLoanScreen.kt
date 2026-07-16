package com.home.library.ui.loan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.home.library.R
import com.home.library.data.local.enums.LoanStatus
import com.home.library.data.local.view.ActiveLoanView
import com.home.library.data.local.view.LoanHistoryRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyLoanScreen(
    onNavigateReturn: () -> Unit,
    onNavigateLogin: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyLoanViewModel = hiltViewModel(),
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_loan_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (!isLoggedIn) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.my_loan_login_required))
                    Button(onClick = onNavigateLogin, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.common_login))
                    }
                }
                return@Column
            }

            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.my_loan_tab_current)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.my_loan_tab_history)) })
            }
            when (tab) {
                0 -> CurrentTab(viewModel, onNavigateReturn)
                else -> HistoryTab(viewModel)
            }
        }
    }
}

@Composable
private fun CurrentTab(viewModel: MyLoanViewModel, onNavigateReturn: () -> Unit) {
    val active by viewModel.active.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onNavigateReturn, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.my_loan_return_action))
        }
        if (active.isEmpty()) {
            Text(stringResource(R.string.my_loan_empty_current))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(active, key = { it.loanId }) { ActiveRow(it) }
            }
        }
    }
}

@Composable
private fun ActiveRow(loan: ActiveLoanView) {
    val now = System.currentTimeMillis()
    val overdue = loan.status == LoanStatus.OVERDUE || loan.dueDate < now
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
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
    }
}

@Composable
private fun HistoryTab(viewModel: MyLoanViewModel) {
    val state by viewModel.history.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.bookQuery,
            onValueChange = viewModel::onBookQueryChange,
            label = { Text(stringResource(R.string.history_book_query)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.fromDate,
            onValueChange = viewModel::onFromDateChange,
            label = { Text(stringResource(R.string.history_from_date)) },
            singleLine = true,
            isError = state.dateError,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.toDate,
            onValueChange = viewModel::onToDateChange,
            label = { Text(stringResource(R.string.history_to_date)) },
            singleLine = true,
            isError = state.dateError,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.dateError) {
            Text(
                text = stringResource(R.string.history_date_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(onClick = viewModel::applyFilters, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.history_apply))
        }

        if (state.records.isEmpty() && !state.loading) {
            Text(stringResource(R.string.history_empty))
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(state.records, key = { it.loanId }) { HistoryRow(it) }
                if (state.canLoadMore) {
                    item {
                        OutlinedButton(
                            onClick = viewModel::loadMore,
                            enabled = !state.loading,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.history_load_more)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(record: LoanHistoryRecord) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(record.bookTitle, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.history_loan_date, formatDate(record.loanDate)),
                style = MaterialTheme.typography.bodySmall,
            )
            val returnText = record.returnDate?.let {
                stringResource(R.string.history_returned, formatDate(it))
            } ?: stringResource(R.string.history_not_returned)
            Text(
                text = "$returnText · ${loanStatusLabel(record.status)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
