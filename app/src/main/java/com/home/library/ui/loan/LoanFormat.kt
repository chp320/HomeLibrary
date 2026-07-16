package com.home.library.ui.loan

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.home.library.R
import com.home.library.data.local.enums.LoanStatus
import java.time.Instant
import java.time.ZoneId

internal const val DAY_MS = 24L * 60 * 60 * 1000

/** epoch millis → YYYY-MM-DD. */
internal fun formatDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

/** 반납예정 잔여/연체 표기. */
@Composable
internal fun remainingText(dueDate: Long, now: Long, overdue: Boolean): String =
    if (overdue) {
        val days = ((now - dueDate) / DAY_MS).toInt().coerceAtLeast(0)
        stringResource(R.string.return_overdue, days)
    } else {
        val days = ((dueDate - now) / DAY_MS).toInt()
        if (days <= 0) stringResource(R.string.return_dday_today) else stringResource(R.string.return_dday, days)
    }

@Composable
internal fun loanStatusLabel(status: LoanStatus): String = when (status) {
    LoanStatus.LOANED -> stringResource(R.string.loan_status_loaned)
    LoanStatus.RETURNED -> stringResource(R.string.loan_status_returned)
    LoanStatus.OVERDUE -> stringResource(R.string.loan_status_overdue)
}
