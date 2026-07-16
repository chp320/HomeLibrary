package com.home.library.ui.book.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.home.library.R
import com.home.library.data.local.entity.BookEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(
    onBookClick: (Long) -> Unit,
    onAddBook: () -> Unit,
    onNavigateScan: () -> Unit,
    onNavigateLoan: () -> Unit,
    onNavigateReturn: () -> Unit,
    onNavigateMyLoan: () -> Unit,
    onNavigateAdmin: () -> Unit,
    onNavigateLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookListViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.book_list_title)) },
                actions = {
                    if (state.isLoggedIn) {
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuOpen = true }) {
                            Text("⋮", style = MaterialTheme.typography.titleLarge)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            MenuItem(stringResource(R.string.my_loan_title)) { menuOpen = false; onNavigateMyLoan() }
                            MenuItem(stringResource(R.string.loan_title)) { menuOpen = false; onNavigateLoan() }
                            MenuItem(stringResource(R.string.return_title)) { menuOpen = false; onNavigateReturn() }
                            if (state.isAdmin) {
                                MenuItem(stringResource(R.string.book_scan_register)) { menuOpen = false; onNavigateScan() }
                                MenuItem(stringResource(R.string.admin_home_title)) { menuOpen = false; onNavigateAdmin() }
                            }
                            MenuItem(stringResource(R.string.common_logout)) { menuOpen = false; viewModel.logout() }
                        }
                    } else {
                        TextButton(onClick = onNavigateLogin) {
                            Text(stringResource(R.string.common_login))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.isAdmin) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.book_add)) },
                    icon = { Text("+", style = MaterialTheme.typography.titleLarge) },
                    onClick = onAddBook,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text(stringResource(R.string.book_search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.availableOnly,
                        onClick = { viewModel.onAvailableOnlyChange(!state.availableOnly) },
                        label = { Text(stringResource(R.string.book_filter_available)) },
                    )
                }
                item {
                    FilterChip(
                        selected = state.category == null,
                        onClick = { viewModel.onCategoryChange(null) },
                        label = { Text(stringResource(R.string.book_filter_all_categories)) },
                    )
                }
                items(state.categories) { category ->
                    FilterChip(
                        selected = state.category == category,
                        onClick = { viewModel.onCategoryChange(category) },
                        label = { Text(category) },
                    )
                }
            }

            if (state.books.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.book_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.books, key = { it.bookId }) { book ->
                        BookRow(book = book, onClick = { onBookClick(book.bookId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, onClick = onClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookRow(book: BookEntity, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            book.author?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(R.string.book_available_count, book.availableQty, book.totalQty),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
