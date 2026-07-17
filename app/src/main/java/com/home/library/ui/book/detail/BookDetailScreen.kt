package com.home.library.ui.book.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.home.library.R
import com.home.library.ui.common.BackButton
import com.home.library.ui.common.BookCover
import com.home.library.data.local.enums.BookStatus
import com.home.library.data.local.enums.LoanAction
import com.home.library.ui.loan.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    onEdit: (Long) -> Unit,
    onLoan: (Long) -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()
    val history by viewModel.history.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLoanBlock by remember { mutableStateOf(false) }

    LaunchedEffect(state.event) {
        when (state.event) {
            is DetailEvent.Deleted -> {
                viewModel.onEventHandled()
                onDeleted()
            }
            is DetailEvent.HasActiveLoans -> {
                showLoanBlock = true
                viewModel.onEventHandled()
            }
            null -> Unit
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.book?.title ?: "") },
                navigationIcon = {
                    BackButton(onBack)
                },
            )
        },
    ) { innerPadding ->
        val book = state.book
        if (book == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) { /* 로딩/삭제됨 */ }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BookCover(
                coverUrl = book.coverUrl,
                modifier = Modifier
                    .width(120.dp)
                    .height(170.dp)
                    .align(Alignment.CenterHorizontally),
            )
            Text(text = book.title, style = MaterialTheme.typography.headlineSmall)

            InfoRow(stringResource(R.string.book_label_author), book.author)
            InfoRow(stringResource(R.string.book_label_publisher), book.publisher)
            InfoRow(stringResource(R.string.book_label_pub_date), book.pubDate)
            InfoRow(stringResource(R.string.book_label_isbn), book.isbn)
            InfoRow(stringResource(R.string.book_label_category), book.category)
            InfoRow(stringResource(R.string.book_label_location), book.location)
            InfoRow(
                stringResource(R.string.book_label_qty),
                stringResource(R.string.book_available_count, book.availableQty, book.totalQty),
            )

            // 대출 버튼(A-5): 누구에게나 보임(비로그인은 대출 화면에서 로그인 유도). 폐기/분실 도서는 숨김.
            // 3분기 — 본인 대출 중 / 가용 0(타인 대출) / 대출 가능.
            // "대출 중"과 "대출 불가"를 구분해야 사용자가 자기 상태인지 남의 상태인지 안다.
            if (book.status == BookStatus.AVAILABLE) {
                val loanModifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                when {
                    state.isBorrowedByMe -> Button(
                        onClick = {},
                        enabled = false,
                        modifier = loanModifier,
                    ) { Text(stringResource(R.string.book_loan_state_borrowed)) }

                    book.availableQty <= 0 -> Button(
                        onClick = {},
                        enabled = false,
                        modifier = loanModifier,
                    ) { Text(stringResource(R.string.book_loan_state_unavailable)) }

                    else -> Button(
                        onClick = { onLoan(book.bookId) },
                        modifier = loanModifier,
                    ) { Text(stringResource(R.string.loan_button)) }
                }
            }

            if (state.isAdmin) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Button(
                        onClick = { onEdit(viewModel.editBookId) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.common_edit)) }
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.common_delete)) }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = stringResource(R.string.book_loan_history_title),
                style = MaterialTheme.typography.titleMedium,
            )
            if (history.isEmpty()) {
                Text(
                    text = stringResource(R.string.book_loan_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                history.forEach { h ->
                    Text(
                        text = "${formatDate(h.actionAt)} · ${loanActionLabel(h.action)} · ${h.actorName}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.book_delete_confirm_title)) },
            text = { Text(stringResource(R.string.book_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.discard()
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showLoanBlock) {
        AlertDialog(
            onDismissRequest = { showLoanBlock = false },
            title = { Text(stringResource(R.string.book_delete_confirm_title)) },
            text = { Text(stringResource(R.string.book_delete_has_loans)) },
            confirmButton = {
                TextButton(onClick = { showLoanBlock = false }) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun loanActionLabel(action: LoanAction): String = when (action) {
    LoanAction.LOAN -> stringResource(R.string.loan_action_loan)
    LoanAction.RETURN -> stringResource(R.string.loan_action_return)
    LoanAction.EXTEND -> stringResource(R.string.loan_action_extend)
    LoanAction.FORCE_RETURN -> stringResource(R.string.loan_action_force_return)
}
