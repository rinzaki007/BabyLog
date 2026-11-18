package com.rinzaki.babylog

import android.content.Context

object FeedingPreferences {
    private const val FILE_NAME = "babylog_feeding_preferences"
    private const val KEY_BREAST_MILK = "show_breast_milk"
    private const val KEY_FORMULA = "show_formula"
    private const val KEY_MIXED = "show_mixed"
    private const val KEY_LAST_METHOD = "last_method"
    private const val KEY_LAST_BREAST_MINUTES = "last_breast_minutes"

    const val BREAST_MILK = "母乳"
    const val FORMULA = "配方奶"
    const val MIXED = "混合喂养"

    fun isEnabled(context: Context, method: String): Boolean {
        val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        return when (method) {
            BREAST_MILK -> preferences.getBoolean(KEY_BREAST_MILK, false)
            FORMULA -> preferences.getBoolean(KEY_FORMULA, true)
            MIXED -> preferences.getBoolean(KEY_MIXED, false)
            else -> false
        }
    }

    fun setEnabled(context: Context, method: String, enabled: Boolean) {
        val key = when (method) {
            BREAST_MILK -> KEY_BREAST_MILK
            FORMULA -> KEY_FORMULA
            MIXED -> KEY_MIXED
            else -> return
        }
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(key, enabled).apply()
    }

    fun enabledMethods(context: Context): List<String> =
        listOf(BREAST_MILK, FORMULA, MIXED).filter { isEnabled(context, it) }

    fun lastMethod(context: Context): String =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).getString(KEY_LAST_METHOD, FORMULA) ?: FORMULA

    fun lastAmountMl(context: Context, method: String): String {
        val key = when (method) {
            BREAST_MILK -> "last_amount_breast"
            MIXED -> "last_amount_mixed"
            else -> "last_amount_formula"
        }
        return context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).getString(key, "").orEmpty()
    }

    fun lastBreastMinutes(context: Context): String =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).getString(KEY_LAST_BREAST_MINUTES, "").orEmpty()

    fun saveLastSelection(context: Context, method: String, amountMl: String, breastMinutes: String) {
        if (method !in listOf(BREAST_MILK, FORMULA, MIXED)) return
        val amountKey = when (method) {
            BREAST_MILK -> "last_amount_breast"
            MIXED -> "last_amount_mixed"
            else -> "last_amount_formula"
        }
        val editor = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_METHOD, method)
        if (amountMl.isNotBlank()) editor.putString(amountKey, amountMl)
        if (breastMinutes.isNotBlank()) editor.putString(KEY_LAST_BREAST_MINUTES, breastMinutes)
        editor.apply()
    }
}

internal fun resolveDefaultFeedingMethod(
    existingMethod: String?,
    savedMethod: String,
    enabledMethods: List<String>
): String {
    val preferred = existingMethod ?: savedMethod
    return if (preferred in enabledMethods) preferred else enabledMethods.firstOrNull() ?: FeedingPreferences.FORMULA
}
