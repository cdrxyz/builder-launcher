package xyz.cdr.builderlauncher.backup

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.autofill.AutofillType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalComposeUiApi::class)
class AccountAutofillTest {
    @Test
    fun emailFieldAsksPasswordManager() {
        assertTrue(AccountAutofill.emailTypes.contains(AutofillType.Username))
        assertTrue(AccountAutofill.emailTypes.contains(AutofillType.EmailAddress))
    }

    @Test
    fun passwordFieldAsksPasswordManager() {
        assertEquals(listOf(AutofillType.Password), AccountAutofill.passwordTypes)
    }
}
