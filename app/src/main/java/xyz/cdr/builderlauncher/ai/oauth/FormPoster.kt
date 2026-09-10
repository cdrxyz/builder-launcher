package xyz.cdr.builderlauncher.ai.oauth

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.cdr.builderlauncher.net.HttpClients

data class FormResponse(val code: Int, val body: String)

fun interface FormPoster {
    fun post(url: String, fields: Map<String, String>, headers: Map<String, String>): FormResponse
}

class OkHttpFormPoster(
    private val http: OkHttpClient = HttpClients.shared,
) : FormPoster {
    override fun post(url: String, fields: Map<String, String>, headers: Map<String, String>): FormResponse {
        val form = FormBody.Builder().also { b ->
            fields.forEach { (k, v) -> b.add(k, v) }
        }.build()
        val req = Request.Builder().url(url).post(form).apply {
            header("Accept", "application/json")
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        http.newCall(req).execute().use { resp ->
            return FormResponse(resp.code, resp.body?.string().orEmpty())
        }
    }
}
