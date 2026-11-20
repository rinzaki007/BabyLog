package com.rinzaki.babylog

import android.content.Context

/** Stores last-used form choices locally; saved event payloads remain the source of truth. */
internal object RecordPreferences {
    private const val FILE_NAME = "babylog_record_preferences"

    const val DIAPER_URINE = "diaper_urine"
    const val DIAPER_STOOL = "diaper_stool"
    const val DIAPER_SHAPE = "diaper_shape"
    const val DIAPER_COLOR = "diaper_color"
    const val DIAPER_AMOUNT = "diaper_amount"
    const val SLEEP_DURATION = "sleep_duration"
    const val FOOD_NAME = "food_name"
    const val FOOD_UNIT = "food_unit"
    const val FOOD_CATEGORY = "food_category"
    const val FOOD_STAGE = "food_stage"
    const val FOOD_STATUS = "food_status"
    const val FOOD_REACTION = "food_reaction"
    const val SUPPLEMENT_NAME = "supplement_name"
    const val SUPPLEMENT_UNIT = "supplement_unit"

    fun get(context: Context, key: String, defaultValue: String): String =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).getString(key, defaultValue) ?: defaultValue

    fun getBoolean(context: Context, key: String, defaultValue: Boolean): Boolean =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).getBoolean(key, defaultValue)

    fun save(context: Context, values: Map<String, String>, flags: Map<String, Boolean> = emptyMap()) {
        val editor = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit()
        values.forEach { (key, value) -> if (value.isNotBlank()) editor.putString(key, value) }
        flags.forEach { (key, value) -> editor.putBoolean(key, value) }
        editor.apply()
    }
}
