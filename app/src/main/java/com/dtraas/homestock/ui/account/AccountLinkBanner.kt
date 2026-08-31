package com.dtraas.homestock.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dtraas.homestock.R

/**
 * One-time nudge shown right after creating/joining a household (see
 * [com.dtraas.homestock.data.repository.HouseholdSession.justJoinedHousehold] and
 * [com.dtraas.homestock.data.repository.AccountLinkRepository.hasShownLinkPrompt]) — catches
 * someone while they're engaged, without making account-linking a blocking step in the
 * household setup flow itself (that flow is deliberately frictionless: no account needed to get
 * started). A persistent banner (2026-08 dialog review) rather than a modal `AlertDialog`: this
 * isn't urgent enough to interrupt whatever screen someone lands on, and staying visible across
 * navigation — instead of vanishing the moment it's dismissed once — gives it more than one
 * chance to be noticed. Meer > Account koppelen remains available afterward either way.
 *
 * Sits above every screen's own top app bar (see HomeStockNavHost), so — unlike those, which all
 * live inside a Scaffold that already zeroes out the top content padding in favor of each
 * header's own `windowInsetsPadding(WindowInsets.statusBars)` — this banner needs that same
 * status-bar clearance itself, or its content starts right under the status bar with only the
 * Row's own 10dp top padding for cushion, reading as almost no space above the text at all next
 * to the same 10dp below it.
 */
@Composable
fun AccountLinkBanner(onLinkNow: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                Text(
                    text = stringResource(R.string.account_link_prompt_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.account_link_prompt_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 2.dp),
                )
                TextButton(onClick = onLinkNow, modifier = Modifier.padding(top = 2.dp)) {
                    Text(stringResource(R.string.account_link_prompt_confirm))
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.account_link_prompt_dismiss),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
