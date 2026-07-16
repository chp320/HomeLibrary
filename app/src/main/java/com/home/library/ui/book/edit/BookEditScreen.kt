package com.home.library.ui.book.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.home.library.R
import com.home.library.book.IsbnError
import com.home.library.book.OptionalTextError
import com.home.library.book.PubDateError
import com.home.library.book.QuantityError
import com.home.library.book.TitleError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookEditScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookEditViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsState()

    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEdit) R.string.book_edit_title_edit else R.string.book_edit_title_new,
                        ),
                    )
                },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.lookupLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.book_lookup_loading),
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            state.lookupNotice?.let { notice ->
                Text(
                    text = lookupNoticeMessage(notice),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.coverUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier
                        .height(140.dp)
                        .padding(bottom = 4.dp),
                )
            }

            FormField(state.title, viewModel::onTitleChange, stringResource(R.string.book_field_title), titleErrorMessage(state.titleError))
            FormField(state.author, viewModel::onAuthorChange, stringResource(R.string.book_field_author), optionalTextErrorMessage(state.authorError))
            FormField(state.publisher, viewModel::onPublisherChange, stringResource(R.string.book_field_publisher), optionalTextErrorMessage(state.publisherError))
            FormField(state.pubDate, viewModel::onPubDateChange, stringResource(R.string.book_field_pub_date), pubDateErrorMessage(state.pubDateError))
            FormField(state.isbn, viewModel::onIsbnChange, stringResource(R.string.book_field_isbn), isbnErrorMessage(state.isbnError))
            FormField(state.category, viewModel::onCategoryChange, stringResource(R.string.book_field_category), optionalTextErrorMessage(state.categoryError))
            FormField(state.location, viewModel::onLocationChange, stringResource(R.string.book_field_location), optionalTextErrorMessage(state.locationError))
            FormField(
                state.quantity,
                viewModel::onQuantityChange,
                stringResource(R.string.book_field_quantity),
                quantityErrorMessage(state.quantityError, state.belowLoaned),
                KeyboardType.Number,
            )

            Button(
                onClick = viewModel::submit,
                enabled = !state.loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                } else {
                    Text(stringResource(R.string.book_save))
                }
            }
        }
    }

    state.duplicate?.let { dup ->
        val message = if (dup.fromScan) {
            stringResource(R.string.book_dup_scan_message, dup.title, dup.currentTotal)
        } else {
            stringResource(R.string.book_dup_message, dup.title, dup.addQty)
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicate,
            title = { Text(stringResource(R.string.book_dup_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmAddQuantity) {
                    Text(stringResource(R.string.common_add))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDuplicate) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    state.addQtyExceeded?.let { max ->
        AlertDialog(
            onDismissRequest = viewModel::onAddQtyExceededShown,
            title = { Text(stringResource(R.string.book_dup_title)) },
            text = { Text(stringResource(R.string.book_error_qty_add_exceeds, max)) },
            confirmButton = {
                TextButton(onClick = viewModel::onAddQtyExceededShown) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
        )
    }
}

@Composable
private fun lookupNoticeMessage(notice: LookupNotice): String = when (notice) {
    LookupNotice.NOT_FOUND -> stringResource(R.string.book_lookup_not_found)
    LookupNotice.AUTH_ERROR -> stringResource(R.string.book_lookup_auth_error)
    LookupNotice.RATE_LIMITED -> stringResource(R.string.book_lookup_rate_limited)
    LookupNotice.NETWORK_ERROR -> stringResource(R.string.book_lookup_network_error)
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val supporting: (@Composable () -> Unit)? = error?.let { msg -> { Text(msg) } }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        supportingText = supporting,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun titleErrorMessage(error: TitleError?): String? = when (error) {
    TitleError.BLANK -> stringResource(R.string.book_error_title_blank)
    TitleError.TOO_LONG -> stringResource(R.string.book_error_title_too_long)
    null -> null
}

@Composable
private fun quantityErrorMessage(error: QuantityError?, belowLoaned: Int?): String? = when {
    belowLoaned != null -> stringResource(R.string.book_error_qty_below_loaned, belowLoaned)
    error == QuantityError.TOO_LARGE -> stringResource(R.string.book_error_qty_too_large)
    error == QuantityError.BLANK -> stringResource(R.string.book_error_qty)
    error == QuantityError.INVALID -> stringResource(R.string.book_error_qty)
    error == QuantityError.TOO_SMALL -> stringResource(R.string.book_error_qty)
    else -> null
}

@Composable
private fun isbnErrorMessage(error: IsbnError?): String? = when (error) {
    IsbnError.FORMAT -> stringResource(R.string.book_error_isbn)
    IsbnError.CHECKSUM -> stringResource(R.string.book_error_isbn_checksum)
    IsbnError.ISBN10_CHECKSUM -> stringResource(R.string.book_error_isbn10_checksum)
    null -> null
}

@Composable
private fun pubDateErrorMessage(error: PubDateError?): String? = when (error) {
    PubDateError.FORMAT -> stringResource(R.string.book_error_pub_date)
    PubDateError.INVALID -> stringResource(R.string.book_error_pub_date_invalid)
    null -> null
}

@Composable
private fun optionalTextErrorMessage(error: OptionalTextError?): String? = when (error) {
    OptionalTextError.TOO_LONG -> stringResource(R.string.book_error_text_too_long)
    null -> null
}
