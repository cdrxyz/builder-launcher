package xyz.cdr.builderlauncher.backup

import android.content.Context
import android.view.autofill.AutofillManager
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.autofill.AutofillType

/** Builder.cdr.xyz login fields on backup settings. */
@OptIn(ExperimentalComposeUiApi::class)
object AccountAutofill {
    val emailTypes = listOf(AutofillType.Username, AutofillType.EmailAddress)
    val passwordTypes = listOf(AutofillType.Password)

    fun commit(context: Context) {
        context.getSystemService(AutofillManager::class.java)?.commit()
    }

    fun cancel(context: Context) {
        context.getSystemService(AutofillManager::class.java)?.cancel()
    }
}
