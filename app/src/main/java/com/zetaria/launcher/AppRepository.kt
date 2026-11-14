package com.zetaria.launcher

import android.content.Context
import android.content.Intent
import java.util.Locale

object AppRepository {

    /** Lista todas las apps lanzables (excepto el propio launcher), ordenadas por nombre. */
    @Suppress("DEPRECATION")
    fun load(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { ri ->
                AppInfo(
                    label = ri.loadLabel(pm).toString(),
                    packageName = ri.activityInfo.packageName,
                    activityName = ri.activityInfo.name,
                    icon = ri.loadIcon(pm)
                )
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }
}
