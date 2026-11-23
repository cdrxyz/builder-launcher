package xyz.cdr.builderlauncher.backup

object AccountRefresh {
    const val SIGNED_OUT = "Not signed in"

    fun shouldRetry(message: String?, refresh: String): Boolean =
        message == SIGNED_OUT && refresh.isNotBlank()

    /** The Worker answers 401 "Not signed in" when the refresh token is gone or expired. */
    fun isExpired(message: String?): Boolean = message == SIGNED_OUT

    fun showsSignIn(email: String, token: String): Boolean = true
}
