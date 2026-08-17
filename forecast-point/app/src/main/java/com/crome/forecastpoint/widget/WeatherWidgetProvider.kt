package com.crome.forecastpoint.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.crome.forecastpoint.MainActivity
import com.crome.forecastpoint.ForecastPointApp
import com.crome.forecastpoint.R
import com.crome.forecastpoint.data.DayForecast
import com.crome.forecastpoint.data.ForecastPeriod
import com.crome.forecastpoint.data.PreferencesRepository
import com.crome.forecastpoint.data.WeatherRepository
import com.crome.forecastpoint.data.WeatherSnapshot
import com.crome.forecastpoint.util.IconMapper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class WeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WeatherWidgetUpdater.updateAll(context)
        val pending = goAsync()
        widgetAppScope(context).launch {
            try {
                WeatherRepository(context.applicationContext).refreshActive(manual = false)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle?,
    ) {
        WeatherWidgetUpdater.updateAll(context)
    }

    override fun onEnabled(context: Context) {
        WeatherWidgetUpdater.updateAll(context)
    }

    companion object {
        const val ACTION_REFRESH = "com.crome.forecastpoint.ACTION_WIDGET_REFRESH"
    }
}

internal fun widgetAppScope(context: Context) =
    (context.applicationContext as? ForecastPointApp)?.applicationScope
        ?: kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        )

object WeatherWidgetUpdater {
    private val dayIconIds = intArrayOf(
        R.id.day0_icon, R.id.day1_icon, R.id.day2_icon,
        R.id.day3_icon, R.id.day4_icon, R.id.day5_icon,
    )
    private val dayNameIds = intArrayOf(
        R.id.day0_name, R.id.day1_name, R.id.day2_name,
        R.id.day3_name, R.id.day4_name, R.id.day5_name,
    )
    private val dayHiIds = intArrayOf(
        R.id.day0_hi, R.id.day1_hi, R.id.day2_hi,
        R.id.day3_hi, R.id.day4_hi, R.id.day5_hi,
    )
    private val dayLoIds = intArrayOf(
        R.id.day0_lo, R.id.day1_lo, R.id.day2_lo,
        R.id.day3_lo, R.id.day4_lo, R.id.day5_lo,
    )

    fun updateAll(context: Context, snapshot: WeatherSnapshot? = null) {
        val appContext = context.applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val component = android.content.ComponentName(appContext, WeatherWidgetProvider::class.java)
        val ids = appWidgetManager.getAppWidgetIds(component)
        if (ids.isEmpty()) return

        widgetAppScope(appContext).launch {
            val prefs = PreferencesRepository(appContext)
            val snap = snapshot ?: prefs.getSnapshotOnce()
            val showHighLow = prefs.getWidgetShowHighLowOnce()
            ids.forEach { id ->
                val minH = appWidgetManager.getAppWidgetOptions(id)
                    ?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110) ?: 110
                val minW = appWidgetManager.getAppWidgetOptions(id)
                    ?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) ?: 250
                val views = buildViews(appContext, snap, showHighLow, minW, minH)
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }

    private fun buildViews(
        context: Context,
        snap: WeatherSnapshot?,
        showHighLow: Boolean,
        minWidthDp: Int,
        minHeightDp: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.weather_widget)

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, openApp)

        // Scale icon bitmaps with widget size (taller/wider → sharper larger icons)
        val dayIconPx = dayIconPixelSize(minWidthDp, minHeightDp)
        val currentIconPx = (dayIconPx * 1.15f).toInt().coerceIn(96, 200)

        if (snap == null) {
            views.setTextViewText(R.id.widget_location, "Point Forecast")
            views.setTextViewText(R.id.widget_datetime, "Open app to load weather")
            views.setTextViewText(R.id.widget_temp, "—")
            return views
        }

        views.setTextViewText(R.id.widget_location, snap.locationName)
        views.setTextViewText(R.id.widget_datetime, formatHeaderDate(snap))
        views.setTextViewText(
            R.id.widget_temp,
            snap.current.temperatureF?.let { "$it° F" } ?: "—",
        )
        views.setTextViewText(R.id.widget_sunrise, snap.sunrise ?: "—")
        views.setTextViewText(R.id.widget_sunset, snap.sunset ?: "—")

        IconMapper.loadBitmap(context, snap.current.iconCode, maxPx = currentIconPx)?.let {
            views.setImageViewBitmap(R.id.widget_current_icon, it)
        }

        if (showHighLow) {
            bindHighLowCells(context, views, snap.days, dayIconPx)
        } else {
            bindPeriodCells(context, views, snap.periods, dayIconPx)
        }
        return views
    }

    /** Larger bitmaps for 5×2 / 5×3 so icons look sharp when layout expands. */
    private fun dayIconPixelSize(minWidthDp: Int, minHeightDp: Int): Int {
        // Rough cell height ~ half of remaining body; scale up on tall widgets
        val fromHeight = when {
            minHeightDp >= 220 -> 180
            minHeightDp >= 160 -> 150
            minHeightDp >= 130 -> 128
            else -> 110
        }
        val fromWidth = when {
            minWidthDp >= 320 -> 170
            minWidthDp >= 280 -> 150
            else -> 120
        }
        return minOf(fromHeight, fromWidth).coerceIn(96, 200)
    }

    /** Classic layout: period name + single temp (like reference "Tue AM 83°"). */
    private fun bindPeriodCells(
        context: Context,
        views: RemoteViews,
        periods: List<ForecastPeriod>,
        iconPx: Int,
    ) {
        val cells = periods.take(6)
        for (index in 0 until 6) {
            val p = cells.getOrNull(index)
            if (p == null) {
                views.setTextViewText(dayNameIds[index], "")
                views.setTextViewText(dayHiIds[index], "")
                views.setViewVisibility(dayLoIds[index], View.GONE)
            } else {
                views.setTextViewText(dayNameIds[index], shortPeriodLabel(p))
                views.setTextViewText(
                    dayHiIds[index],
                    p.temperatureF?.let { "$it° F" } ?: "—",
                )
                views.setViewVisibility(dayLoIds[index], View.GONE)
                IconMapper.loadBitmap(context, p.iconCode, maxPx = iconPx)?.let {
                    views.setImageViewBitmap(dayIconIds[index], it)
                }
            }
        }
    }

    /** Day high/low mode: ↑ high, ↓ low under the day name. */
    private fun bindHighLowCells(
        context: Context,
        views: RemoteViews,
        days: List<DayForecast>,
        iconPx: Int,
    ) {
        for (index in 0 until 6) {
            val day = days.getOrNull(index)
            if (day == null) {
                views.setTextViewText(dayNameIds[index], "")
                views.setTextViewText(dayHiIds[index], "")
                views.setTextViewText(dayLoIds[index], "")
                views.setViewVisibility(dayLoIds[index], View.GONE)
            } else {
                views.setTextViewText(dayNameIds[index], day.dayName)
                // Arrows after the number (e.g. "79° ↑" / "61° ↓"), matching preferred layout.
                views.setTextViewText(
                    dayHiIds[index],
                    day.highF?.let { "$it° ↑" } ?: (day.lowF?.let { "$it°" } ?: "—"),
                )
                if (day.lowF != null && day.highF != null) {
                    views.setViewVisibility(dayLoIds[index], View.VISIBLE)
                    views.setTextViewText(dayLoIds[index], "${day.lowF}° ↓")
                } else {
                    views.setViewVisibility(dayLoIds[index], View.GONE)
                }
                IconMapper.loadBitmap(context, day.iconCode, maxPx = iconPx)?.let {
                    views.setImageViewBitmap(dayIconIds[index], it)
                }
            }
        }
    }

    /**
     * "Monday Night" → "Mon PM", "Tuesday" → "Tue AM", "This Afternoon" → "Today",
     * "Tonight" → "Tonight" — similar to classic NWS period widgets.
     */
    private fun shortPeriodLabel(period: ForecastPeriod): String {
        val n = period.name.trim()
        when {
            n.equals("Tonight", ignoreCase = true) -> return "Tonight"
            n.equals("This Afternoon", ignoreCase = true) -> return "Today"
            n.equals("Today", ignoreCase = true) -> return "Today"
            n.equals("Overnight", ignoreCase = true) -> return "Overnight"
        }
        val isNight = n.contains("Night", ignoreCase = true) ||
            n.contains("Evening", ignoreCase = true)
        val base = n
            .removeSuffix(" Night")
            .removeSuffix(" night")
            .removeSuffix(" Evening")
            .removePrefix("This ")
            .trim()
        val dow = when {
            base.length >= 3 -> base.take(3)
            else -> base
        }
        // Prefer calendar weekday when ISO is present
        val fromIso = period.startTimeIso?.let { iso ->
            try {
                val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                    .parse(iso.take(19))
                parsed?.let { SimpleDateFormat("EEE", Locale.US).format(it) }
            } catch (_: Exception) {
                null
            }
        }
        val day = fromIso ?: dow
        val suffix = if (isNight || !period.isDaytime) "PM" else "AM"
        // Full-day names that are clearly daytime stay "AM"
        return "$day $suffix"
    }

    private fun formatHeaderDate(snap: WeatherSnapshot): String {
        val obs = snap.observationTimeLabel
        if (!obs.isNullOrBlank()) return obs
        val fmt = SimpleDateFormat("EEEE, MMMM d, yyyy h:mm a", Locale.US)
        return fmt.format(java.util.Date(snap.updatedAtEpochMs))
    }
}
