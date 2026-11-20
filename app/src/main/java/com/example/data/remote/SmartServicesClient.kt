package com.example.data.remote

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class WeatherReport(
    val locationName: String,
    val temperatureC: Double,
    val feelsLikeC: Double,
    val humidityPercent: Int,
    val windKmh: Double,
    val condition: String
)

data class NewsHeadline(
    val title: String,
    val source: String,
    val link: String,
    val publishedAt: String
)

class SmartServicesClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    suspend fun fetchWeatherByCoordinates(
        latitude: Double,
        longitude: Double,
        label: String = "Current Location"
    ): Result<WeatherReport> = withContext(Dispatchers.IO) {
        try {
            val url =
                "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m&timezone=auto"
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Weather HTTP ${resp.code}"))
                }
                val root = JSONObject(resp.body?.string().orEmpty())
                val current = root.getJSONObject("current")
                Result.success(
                    WeatherReport(
                        locationName = label,
                        temperatureC = current.optDouble("temperature_2m", 0.0),
                        feelsLikeC = current.optDouble("apparent_temperature", 0.0),
                        humidityPercent = current.optInt("relative_humidity_2m", 0),
                        windKmh = current.optDouble("wind_speed_10m", 0.0),
                        condition = mapWmoCode(current.optInt("weather_code", 0))
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchWeatherByCity(city: String): Result<WeatherReport> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(city.trim().ifBlank { "New Delhi" }, "UTF-8")
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json"
            val geoReq = Request.Builder().url(geoUrl).get().build()
            httpClient.newCall(geoReq).execute().use { geoResp ->
                if (!geoResp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Geocoding HTTP ${geoResp.code}"))
                }
                val geoRoot = JSONObject(geoResp.body?.string().orEmpty())
                val results = geoRoot.optJSONArray("results")
                if (results == null || results.length() == 0) {
                    return@withContext Result.failure(IllegalStateException("City '$city' not found"))
                }
                val first = results.getJSONObject(0)
                val lat = first.getDouble("latitude")
                val lon = first.getDouble("longitude")
                val name = listOfNotNull(
                    first.optString("name").takeIf { it.isNotBlank() },
                    first.optString("country").takeIf { it.isNotBlank() }
                ).joinToString(", ")
                fetchWeatherByCoordinates(lat, lon, name)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDailyNews(hindi: Boolean = false): Result<List<NewsHeadline>> = withContext(Dispatchers.IO) {
        try {
            val rssUrl = if (hindi) {
                "https://news.google.com/rss?hl=hi&gl=IN&ceid=IN:hi"
            } else {
                "https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en"
            }
            val req = Request.Builder()
                .url(rssUrl)
                .header("User-Agent", "JarvisVIP-Android/1.0")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("News RSS HTTP ${resp.code}"))
                }
                val xml = resp.body?.string().orEmpty()
                Result.success(parseRssItems(xml).take(10))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseRssItems(xml: String): List<NewsHeadline> {
        val items = mutableListOf<NewsHeadline>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var eventType = parser.eventType
        var insideItem = false
        var title = ""
        var link = ""
        var pubDate = ""
        var source = "News"

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name
                    if (tag == "item") {
                        insideItem = true
                        title = ""
                        link = ""
                        pubDate = ""
                        source = "News"
                    } else if (insideItem) {
                        when (tag) {
                            "title" -> title = parser.nextText().trim()
                            "link" -> link = parser.nextText().trim()
                            "pubDate" -> pubDate = parser.nextText().trim().take(22)
                            "source" -> source = parser.nextText().trim()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item" && insideItem) {
                        if (title.isNotBlank()) {
                            items.add(
                                NewsHeadline(
                                    title = title,
                                    source = source,
                                    link = link,
                                    publishedAt = pubDate
                                )
                            )
                        }
                        insideItem = false
                    }
                }
            }
            eventType = parser.next()
        }
        return items
    }

    private fun mapWmoCode(code: Int): String = when (code) {
        0 -> "Clear Sky"
        1, 2 -> "Partly Cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog / Mist"
        51, 53, 55 -> "Light Drizzle"
        61, 63, 65 -> "Rain"
        71, 73, 75 -> "Snow"
        80, 81, 82 -> "Rain Showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Variable Conditions"
    }
}
