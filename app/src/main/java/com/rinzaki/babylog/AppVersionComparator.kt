package com.rinzaki.babylog

/**
 * Installed package metadata used for update comparisons.
 *
 * The display version name is intentionally kept separate from Android's
 * monotonically increasing version code.
 */
internal data class InstalledAppVersion(
    val name: String,
    val code: Long
)

/**
 * Compares releases using Android versionCode whenever the update source
 * provides one. Version-name comparison is retained for older NAS manifests
 * and legacy update sources that do not expose a version code.
 */
internal object AppVersionComparator {
    fun isNewer(
        latestVersion: String,
        latestVersionCode: Long?,
        currentVersion: String,
        currentVersionCode: Long
    ): Boolean {
        if (latestVersionCode != null && latestVersionCode > 0L && currentVersionCode > 0L) {
            return latestVersionCode > currentVersionCode
        }
        return compareVersionNames(latestVersion, currentVersion) > 0
    }

    internal fun compareVersionNames(first: String, second: String): Int {
        val a = normalize(first).split('.').map { it.toIntOrNull() ?: 0 }
        val b = normalize(second).split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(index) { 0 }
            val right = b.getOrElse(index) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }

    private fun normalize(raw: String): String {
        val base = raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        // BabyLog briefly published a build with versionName "3.03".
        // It was intended to be 3.0.3, not decimal version 3.03.
        if (base == "3.03") return "3.0.3"
        return base
    }
}
