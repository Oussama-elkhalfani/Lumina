package com.lumina.tv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Item(
    val id: Int,
    val title: String,
    val overview: String,
    val year: String,
    val genre: String,
    val backdrop: String?,
    val poster: String?
)

data class Content(
    val trending: List<Item>,
    val movies: List<Item>,
    val series: List<Item>,
    val topMovies: List<Item>,
    val topSeries: List<Item>
)

object Sample {
    private fun mk(id: Int, t: String, y: String, g: String, o: String) = Item(id, t, o, y, g, null, null)

    val movies = listOf(
        mk(2, "Midnight Atlas", "2025", "Thriller", "A cartographer discovers that her maps are quietly rewriting the city they describe."),
        mk(4, "The Quiet Orbit", "2024", "Sci-Fi", "Three astronauts drift beyond contact and must decide what to tell Earth."),
        mk(6, "Paper Harbor", "2023", "Drama", "A shipping town braces for one last winter before the docks close for good."),
        mk(8, "Ember Line", "2025", "Action", "A retired rail inspector is pulled back in when a night train goes missing."),
        mk(10, "Salt & Static", "2022", "Mystery", "A radio host receives broadcasts from a station that burned down decades ago."),
        mk(12, "Golden Hour", "2024", "Romance", "Two strangers share one perfect evening in a city neither calls home."),
        mk(14, "Northbound", "2023", "Adventure", "A father and daughter cross a frozen continent to reach a promised cabin."),
        mk(16, "Low Tide", "2021", "Crime", "A detective's last case begins when the sea returns something it shouldn't.")
    )
    val series = listOf(
        mk(101, "Kingfisher Row", "2025", "Drama", "Life on one street, told over a single year through its many front doors."),
        mk(103, "The Long Signal", "2024", "Sci-Fi", "A deep-space listening post picks up a message addressed to a single person."),
        mk(105, "Hollow Pines", "2023", "Mystery", "A small town's secrets surface when the lake is drained for the first time."),
        mk(107, "Verdant", "2025", "Fantasy", "An apprentice gardener inherits a greenhouse that grows more than plants."),
        mk(109, "Blackwater Club", "2022", "Thriller", "Members of an exclusive club start receiving invitations to their own funerals."),
        mk(111, "Afterglow", "2024", "Drama", "Former bandmates reunite for one last tour and the truth about the first one."),
        mk(113, "Cold Harbour", "2023", "Crime", "A harbour master investigates a string of ships arriving with no crew."),
        mk(115, "Parallel Lines", "2025", "Sci-Fi", "Two commuters notice they are living the same day in different versions of the city.")
    )
    val content = Content(
        trending = movies.take(4) + series.take(4),
        movies = movies,
        series = series,
        topMovies = movies.reversed(),
        topSeries = series.reversed()
    )
}

private val GENRES = mapOf(
    28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime",
    99 to "Documentary", 18 to "Drama", 10751 to "Family", 14 to "Fantasy", 27 to "Horror",
    9648 to "Mystery", 10749 to "Romance", 878 to "Sci-Fi", 53 to "Thriller",
    10759 to "Action & Adventure", 10765 to "Sci-Fi & Fantasy"
)

private fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k)

object Repo {
    private suspend fun fetch(path: String, tv: Boolean): List<Item> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.themoviedb.org/3/$path?api_key=${BuildConfig.TMDB_KEY}&language=en-US")
            val c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            val txt = c.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONObject(txt).getJSONArray("results")
            val out = ArrayList<Item>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val title = o.str("title").ifBlank { o.str("name") }
                val bd = o.str("backdrop_path")
                if (title.isBlank() || bd.isBlank()) continue
                val isTv = if (o.has("media_type")) o.str("media_type") == "tv" else tv
                val date = o.str("release_date").ifBlank { o.str("first_air_date") }
                val g = (o.optJSONArray("genre_ids")?.let { if (it.length() > 0) GENRES[it.getInt(0)] else null })
                    ?: (if (isTv) "Series" else "Movie")
                out.add(
                    Item(
                        id = o.getInt("id") * 2 + (if (isTv) 1 else 0),
                        title = title,
                        overview = o.str("overview"),
                        year = date.take(4),
                        genre = g,
                        backdrop = bd,
                        poster = o.str("poster_path").ifBlank { null }
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun loadAll(): Content {
        if (BuildConfig.TMDB_KEY.isBlank()) return Sample.content
        return coroutineScope {
            val a = async { fetch("trending/all/week", false) }
            val b = async { fetch("movie/popular", false) }
            val c = async { fetch("tv/popular", true) }
            val d = async { fetch("movie/top_rated", false) }
            val e = async { fetch("tv/top_rated", true) }
            Content(
                trending = a.await().ifEmpty { Sample.content.trending },
                movies = b.await().ifEmpty { Sample.movies },
                series = c.await().ifEmpty { Sample.series },
                topMovies = d.await().ifEmpty { Sample.content.topMovies },
                topSeries = e.await().ifEmpty { Sample.content.topSeries }
            )
        }
    }
}
