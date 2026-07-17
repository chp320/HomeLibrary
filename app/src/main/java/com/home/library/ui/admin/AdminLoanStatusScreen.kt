package com.home.library.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.home.library.R
import com.home.library.ui.common.BackButton
import com.home.library.ui.common.HomeButton
import com.home.library.data.local.enums.LoanStatus
import com.home.library.ui.loan.formatDate
import com.home.library.ui.loan.loanStatusLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLoanStatusScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminLoanStatusViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_loan_status_title)) },
                navigationIcon = {
                    BackButton(onBack)
                },
                actions = {
                    HomeButton(onHome)
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            when {
                !state.isAdmin -> Text(stringResource(R.string.admin_no_permission))
                state.loans.isEmpty() -> Text(stringResource(R.string.admin_loan_status_empty))
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.loans, key = { it.loanId }) { loan ->
                        val overdue = loan.status == LoanStatus.OVERDUE
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(loan.bookTitle, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = stringResource(R.string.admin_loan_borrower, loan.borrowerName),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = stringResource(R.string.return_due, formatDate(loan.dueDate)) +
                                        " · " + loanStatusLabel(loan.status),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
