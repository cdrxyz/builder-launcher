package xyz.cdr.minimos.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

data class LaunchableApp(
    val label: String,
    val packageName: String,
    val activityName: String,
)

class InstalledApps(private val context: Context) {
    fun all(): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val flags = PackageManager.MATCH_ALL
        val resolved: List<ResolveInfo> = pm.queryIntentActivities(intent, flags)
        return resolved.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            if (activity.packageName == context.packageName) return@mapNotNull null
            LaunchableApp(
                label = info.loadLabel(pm).toString(),
                packageName = activity.packageName,
                activityName = activity.name,
            )
        }.sortedBy { it.label.lowercase() }
    }

    fun search(query: String): List<LaunchableApp> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all()
        return all().filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    fun launch(app: LaunchableApp) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.activityName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
