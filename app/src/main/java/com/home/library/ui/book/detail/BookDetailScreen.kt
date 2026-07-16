package com.home.library.ui.book.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.home.library.R
import com.home.library.data.local.enums.BookStatus

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
                    TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
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

            // 대출하기: 누구에게나 보임(비로그인은 대출 화면에서 로그인 유도). 폐기/분실 도서는 숨김.
            if (book.status == BookStatus.AVAILABLE) {
                Button(
                    onClick = { onLoan(book.bookId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) { Text(stringResource(R.string.loan_button)) }
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
