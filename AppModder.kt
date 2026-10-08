package com.gamehacker.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

object AppModder {

    data class AppInfo(val name: String, val packageName: String, val isSystem: Boolean)

    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps.map {
            val label = try {
                pm.getApplicationLabel(it).toString()
            } catch (_: Exception) {
                it.packageName
            }
            AppInfo(
                name = label,
                packageName = it.packageName,
                isSystem = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }.sortedBy { it.name.lowercase() }
    }

    fun forceStop(packageName: String): String {
        return RootUtils.execute("am force-stop $packageName")
    }

    fun clearData(packageName: String): String {
        return RootUtils.execute("pm clear $packageName")
    }

    fun disableApp(packageName: String): String {
        return RootUtils.execute("pm disable-user --user 0 $packageName")
    }

    fun enableApp(packageName: String): String {
        return RootUtils.execute("pm enable $packageName")
    }

    fun killProcess(packageName: String): String {
        return RootUtils.execute("am kill $packageName")
    }

    /** محاولة بسيطة لـ "Unlock Premium" عن طريق تعطيل بعض المكونات الشائعة */
    fun tryUnlockPremium(packageName: String): String {
        val log = StringBuilder()
        log.appendLine("[Unlock Premium Attempt] $packageName")
        // أمثلة على مكونات شائعة مرتبطة بالشراء / التحقق
        val components = listOf(
            "$packageName/.billing.BillingService",
            "$packageName/.iap.IAPService",
            "$packageName/.premium.PremiumCheck",
            "$packageName/com.android.vending.billing.InAppBillingService.COIN"
        )
        components.forEach { comp ->
            val r = RootUtils.execute("pm disable $comp")
            log.appendLine("disable $comp → $r")
        }
        log.appendLine("ملاحظة: النجاح يعتمد على بنية التطبيق. أفضل طريقة هي APK patching خارجي.")
        return log.toString()
    }

    /** محاولة إزالة إعلانات عن طريق تعطيل Ad Activities الشائعة */
    fun tryRemoveAds(packageName: String): String {
        val log = StringBuilder()
        log.appendLine("[Remove Ads Attempt] $packageName")
        val adComponents = listOf(
            "com.google.android.gms.ads",
            "com.google.android.gms.ads.AdActivity",
            "com.unity3d.services.ads",
            "com.facebook.ads",
            "com.applovin",
            "com.vungle",
            "com.ironsource"
        )
        // تعطيل أي activity فيها كلمة ad (تقريبي)
        val result = RootUtils.execute("pm dump $packageName | grep -i 'activity.*ad'")
        log.appendLine("Ad-related components found:\n$result")
        log.appendLine("لنتائج أفضل استخدم Lucky Patcher أو apktool + smali edit.")
        return log.toString()
    }
}
