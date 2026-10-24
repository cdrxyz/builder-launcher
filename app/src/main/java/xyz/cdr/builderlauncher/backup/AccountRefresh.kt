package xyz.cdr.builderlauncher.backup

object AccountRefresh {
    const val SIGNED_OUT = "Not signed in"

    fun shouldRetry(message: String?, refresh: String): Boolean =
        message == SIGNED_OUT && refresh.isNotBlank()
}
