package com.crome.forecastpoint.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Maps NWS icon URLs / codes onto bundled PNGs under assets/nws_icons/.
 *
 * Bundling the full NWS forecast-icon set (day + night) avoids weather.gov
 * Akamai 403s that break stock Android User-Agents, and works offline.
 *
 * Covers sunny/clear, clouds, rain, snow, sleet, thunder, fog, haze, smoke,
 * dust, wind, hot/cold, blizzard, hurricane, tropical storm, tornado, etc.
 */
object IconMapper {
    private const val MAX_CACHE = 48

    private val cache = object : LinkedHashMap<String, Bitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            if (size <= MAX_CACHE) return false
            eldest?.value?.recycle()
            return true
        }
    }
    private val cacheLock = Any()
    private val available = ConcurrentHashMap<String, Boolean>()

    /**
     * Normalize MapClick / api.weather.gov icon strings to a base asset key
     * (without `.png`). Handles DualImage `i=` params, paths, and legacy aliases.
     */
    fun codeFrom(raw: String?): String {
        if (raw.isNullOrBlank()) return "skc"
        var s = raw.trim()

        if (s.contains("DualImage.php", ignoreCase = true)) {
            val iParam = Regex("[?&]i=([^&]+)").find(s)?.groupValues?.getOrNull(1)
            if (!iParam.isNullOrBlank()) s = iParam
        }

        // api.weather.gov style: .../icons/land/day/smoke or .../night/smoke
        val landMatch = Regex(
            """/icons/land/(day|night)/([a-z0-9_]+)""",
            RegexOption.IGNORE_CASE,
        ).find(s)
        if (landMatch != null) {
            val period = landMatch.groupValues[1].lowercase(Locale.US)
            val code = landMatch.groupValues[2].lowercase(Locale.US)
            s = if (period == "night") "n$code" else code
        }

        if (s.contains('/')) {
            s = s.substringAfterLast('/')
        }
        s = s.substringBefore('?')
        s = s.removeSuffix(".png").removeSuffix(".PNG")
        // Strip trailing chance digits sometimes glued to codes (e.g. shra80 → shra)
        s = s.replace(Regex("(\\D)\\d{1,3}$"), "$1")
        s = s.replace(Regex("\\d+$"), "")

        return normalizeAlias(s.lowercase(Locale.US)).ifBlank { "skc" }
    }

    /**
     * Map synonymous / legacy NWS codes onto the bundled asset names.
     * Modern api.weather.gov names are preferred; MapClick short codes still work.
     */
    private fun normalizeAlias(code: String): String = when (code) {
        // Sky cover
        "clr", "sunny", "clear" -> "skc"
        "nclr", "nclear", "nsunny" -> "nskc"
        "pcloudy", "partlycloudy" -> "sct"
        "npcloudy" -> "nsct"
        "mcloudy", "mostlycloudy" -> "bkn"
        "nmcloudy" -> "nbkn"
        "cloudy", "overcast" -> "ovc"
        "ncloudy" -> "novc"

        // Fog / mist / haze / smoke / dust (air quality / wildfire / dust storms)
        "fg", "mist", "fog" -> "fog"
        "nfg", "nmist", "nfog" -> "nfog"
        "haze", "hz" -> "haze"
        "nhaze", "nhz" -> "nhaze"
        "smoke", "fu", "smoke_haze" -> "smoke"
        "nsmoke", "nfu" -> "nsmoke"
        "dust", "du", "sand", "blowing_dust" -> "dust"
        "ndust", "ndu", "nsand" -> "ndust"

        // Rain / showers
        "ra", "rain" -> "rain"
        "nra", "nrain" -> "nrain"
        "shra", "rain_showers", "showers" -> "rain_showers"
        "nshra", "nrain_showers", "nshowers" -> "nrain_showers"
        "hi_shwrs", "rain_showers_hi", "hi_shra" -> "rain_showers_hi"
        "hi_nshwrs", "nhi_shwrs", "nrain_showers_hi", "nhi_shra" -> "nrain_showers_hi"
        "shra80", "nshra80" -> if (code.startsWith("n")) "nrain_showers" else "rain_showers"

        // Mixes
        "ra_sn", "rasn", "rain_snow", "mix" -> "rain_snow"
        "nra_sn", "nrasn", "nrain_snow", "nmix" -> "nrain_snow"
        "ra_ip", "raip", "rain_sleet" -> "rain_sleet"
        "nra_ip", "nraip", "nrain_sleet" -> "nrain_sleet"
        "fzra", "freezingrain" -> "fzra"
        "nfzra" -> "nfzra"
        "ra_fzra", "rain_fzra" -> "rain_fzra"
        "nra_fzra", "nrain_fzra" -> "nrain_fzra"
        "sn_fzra", "snow_fzra" -> "snow_fzra"
        "nsn_fzra", "nsnow_fzra" -> "nsnow_fzra"

        // Snow / sleet / ice
        "sn", "snow" -> "snow"
        "nsn", "nsnow" -> "nsnow"
        "ip", "sleet", "icepellets" -> "sleet"
        "nip", "nsleet" -> "nsleet"
        "sn_ip", "snow_sleet" -> "snow_sleet"
        "nsn_ip", "nsnow_sleet" -> "nsnow_sleet"
        "blizzard" -> "blizzard"
        "nblizzard" -> "nblizzard"

        // Thunder
        "tsra", "tstorms", "thunderstorm", "thunderstorms" -> "tsra"
        "ntsra", "ntstorms" -> "ntsra"
        "scttsra", "tsra_sct" -> "tsra_sct"
        "nscttsra", "ntsra_sct" -> "ntsra_sct"
        "hi_tsra", "tsra_hi" -> "tsra_hi"
        "hi_ntsra", "nhi_tsra", "ntsra_hi" -> "ntsra_hi"

        // Extreme / tropical
        "hot" -> "hot"
        "nhot" -> "nhot"
        "cold" -> "cold"
        "ncold" -> "ncold"
        "hurricane", "hur" -> "hurricane"
        "nhurricane" -> "nhurricane"
        "tropical_storm", "tropstorm", "ts" -> "tropical_storm"
        "ntropical_storm" -> "ntropical_storm"
        "tornado", "tor" -> "tornado"
        "ntornado" -> "ntornado"

        else -> code
    }

    fun hasAsset(context: Context, code: String): Boolean {
        val key = code.lowercase(Locale.US)
        available[key]?.let { return it }
        val ok = try {
            context.assets.open("nws_icons/$key.png").close()
            true
        } catch (_: IOException) {
            false
        }
        available[key] = ok
        return ok
    }

    fun resolveCode(context: Context, raw: String?): String {
        val primary = codeFrom(raw)
        if (hasAsset(context, primary)) return primary

        val night = primary.startsWith("n") && primary.length > 1
        val base = if (night) primary.drop(1) else primary

        val candidates = buildList {
            add(primary)
            if (night) {
                add(base)
                add("n$base")
            } else {
                add("n$base")
            }
            // Generic weather-family fallbacks
            when {
                base.contains("tsra") || base.contains("tstorm") -> {
                    add(if (night) "ntsra" else "tsra")
                }
                base.contains("shra") || base.contains("shower") || base.contains("rain") -> {
                    add(if (night) "nrain" else "rain")
                    add(if (night) "nrain_showers" else "rain_showers")
                }
                base.contains("snow") || base == "sn" || base.contains("blizzard") -> {
                    add(if (night) "nsnow" else "snow")
                }
                base.contains("fog") || base == "fg" -> {
                    add(if (night) "nfog" else "fog")
                }
                base.contains("smoke") || base == "fu" -> {
                    add(if (night) "nsmoke" else "smoke")
                }
                base.contains("haze") -> {
                    add(if (night) "nhaze" else "haze")
                }
                base.contains("dust") || base == "du" -> {
                    add(if (night) "ndust" else "dust")
                }
                base.contains("wind") -> {
                    add(if (night) "nwind_sct" else "wind_sct")
                }
            }
            add(if (night) "nfew" else "few")
            add(if (night) "nskc" else "skc")
            add("few")
            add("skc")
        }
        return candidates.firstOrNull { hasAsset(context, it) } ?: "skc"
    }

    /**
     * For widgets / RemoteViews only. UI should use Coil [AsyncImage] via NwsIcon.
     * Uses RGB_565 for smaller memory when possible and an LRU of decoded bitmaps.
     */
    fun loadBitmap(context: Context, raw: String?, maxPx: Int = 96): Bitmap? {
        val code = resolveCode(context, raw)
        val cacheKey = "$code@$maxPx"
        synchronized(cacheLock) {
            cache[cacheKey]?.let { return it }
        }

        return try {
            context.assets.open("nws_icons/$code.png").use { stream ->
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inSampleSize = 1
                }
                val decoded = BitmapFactory.decodeStream(stream, null, opts) ?: return null
                val scaled = if (decoded.width > maxPx || decoded.height > maxPx) {
                    val scale = maxPx.toFloat() / maxOf(decoded.width, decoded.height)
                    Bitmap.createScaledBitmap(
                        decoded,
                        (decoded.width * scale).toInt().coerceAtLeast(1),
                        (decoded.height * scale).toInt().coerceAtLeast(1),
                        true,
                    ).also { if (it !== decoded) decoded.recycle() }
                } else {
                    decoded
                }
                synchronized(cacheLock) {
                    cache[cacheKey] = scaled
                }
                scaled
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clearCache() {
        synchronized(cacheLock) {
            cache.values.forEach { it.recycle() }
            cache.clear()
        }
    }
}
