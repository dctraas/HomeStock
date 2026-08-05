package com.dtraas.boodschapbeheer.ui.account

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dtraas.boodschapbeheer.R

/**
 * One-time nudge shown right after creating/joining a household (see
 * [com.dtraas.boodschapbeheer.data.repository.HouseholdSession.justJoinedHousehold] and
 * [com.dtraas.boodschapbeheer.data.repository.AccountLinkRepository.hasShownLinkPrompt]) —
 * catches someone while they're engaged, without making account-linking a blocking step in
 * the household setup flow itself (that flow is deliberately frictionless: no account needed
 * to get started). Dismissible either way; Meer > Account koppelen remains available afterward.
 */
@Composable
fun AccountLinkPromptDialog(onLinkNow: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_link_prompt_title)) },
        text = { Text(stringResource(R.string.account_link_prompt_message)) },
        confirmButton = {
            TextButton(onClick = onLinkNow) { Text(stringResource(R.string.account_link_prompt_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.account_link_prompt_dismiss)) }
        },
    )
}
