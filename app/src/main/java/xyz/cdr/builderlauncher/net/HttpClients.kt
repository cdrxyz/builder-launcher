package xyz.cdr.builderlauncher.net

import okhttp3.CookieJar
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object HttpClients {
    val shared: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    fun derived(
        connectSec: Long = 10,
        readSec: Long = 10,
        writeSec: Long? = null,
        cookieJar: CookieJar? = null,
    ): OkHttpClient = shared.newBuilder().apply {
        connectTimeout(connectSec, TimeUnit.SECONDS)
        readTimeout(readSec, TimeUnit.SECONDS)
        if (writeSec != null) writeTimeout(writeSec, TimeUnit.SECONDS)
        if (cookieJar != null) cookieJar(cookieJar)
    }.build()
}
