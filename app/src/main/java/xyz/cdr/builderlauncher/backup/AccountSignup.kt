package xyz.cdr.builderlauncher.backup

object AccountSignup {
    const val CODE = "timetobuild"

    fun opens(code: String): Boolean = code.trim().lowercase() == CODE
}
