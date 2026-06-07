package eu.kanade.tachiyomi.animeextension.en.gogoanime

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class Gogoanime : AnimeHttpSource() {

    override val name = "Gogoanime"
    override val baseUrl = "https://gogoanime.by"
    override val lang = "en"
    override val supportsLatest = true

    // ======================== Popular ========================

    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/popular.html?page=$page", headers)

    override fun popularAnimeParse(response: Response): AnimesPage {
        val doc = response.asJsoup()
        val animes = doc.select("ul.items li").map { el ->
            SAnime.create().apply {
                title = el.select("p.name a").text()
                setUrlWithoutDomain(el.select("p.name a").attr("href"))
                thumbnail_url = el.select("img").attr("src")
            }
        }
        val hasNext = doc.select("ul.pagination-list li.selected + li").isNotEmpty()
        return AnimesPage(animes, hasNext)
    }

    // ======================== Latest ========================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/?page=$page", headers)

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val doc = response.asJsoup()
        val animes = doc.select("ul.items li").map { el ->
            SAnime.create().apply {
                title = el.select("p.name a").text()
                setUrlWithoutDomain(el.select("p.name a").attr("href"))
                thumbnail_url = el.select("img").attr("src")
            }
        }
        val hasNext = doc.select("ul.pagination-list li.selected + li").isNotEmpty()
        return AnimesPage(animes, hasNext)
    }

    // ======================== Search ========================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
        GET("$baseUrl/search.html?keyword=${query.trim()}&page=$page", headers)

    override fun searchAnimeParse(response: Response): AnimesPage {
        val doc = response.asJsoup()
        val animes = doc.select("ul.items li").map { el ->
            SAnime.create().apply {
                title = el.select("p.name a").text()
                setUrlWithoutDomain(el.select("p.name a").attr("href"))
                thumbnail_url = el.select("img").attr("src")
            }
        }
        val hasNext = doc.select("ul.pagination-list li.selected + li").isNotEmpty()
        return AnimesPage(animes, hasNext)
    }

    // ======================== Details ========================

    override fun animeDetailsParse(response: Response): SAnime {
        val doc = response.asJsoup()
        return SAnime.create().apply {
            title = doc.select("div.anime_info_body_bg h1").text()
            thumbnail_url = doc.select("div.anime_info_body_bg img").attr("src")
            description = doc.select("div.description").text()
            genre = doc.select("p.type:contains(Genre) a").joinToString { it.text() }
            status = when (doc.select("p.type:contains(Status) a").text().lowercase()) {
                "ongoing" -> SAnime.ONGOING
                "completed" -> SAnime.COMPLETED
                else -> SAnime.UNKNOWN
            }
        }
    }

    // ======================== Episodes ========================

    override fun episodeListRequest(anime: SAnime): Request =
        GET(baseUrl + anime.url, headers)

    override fun episodeListParse(response: Response): List<SEpisode> {
        val doc = response.asJsoup()

        // Get episode range from the hidden input fields
        val animeId = doc.select("#movie_id").attr("value")
        val epStart = doc.select("#episode_page li a").first()?.attr("ep_start") ?: "0"
        val epEnd = doc.select("#episode_page li a").last()?.attr("ep_end") ?: "0"
        val alias = doc.select("#alias_anime").attr("value")

        // Fetch full episode list from the ajax endpoint
        val ajaxUrl = "https://ajax.gogo-load.com/ajax/load-list-episode" +
            "?ep_start=$epStart&ep_end=$epEnd&id=$animeId&default_ep=0&alias=$alias"
        val ajaxResponse = client.newCall(GET(ajaxUrl, headers)).execute()
        val ajaxDoc = ajaxResponse.asJsoup()

        return ajaxDoc.select("ul#episode_related li").map { el ->
            SEpisode.create().apply {
                name = "Episode " + el.select("div.name").text().trim().removePrefix("EP ")
                setUrlWithoutDomain(el.select("a").attr("href").trim())
                episode_number = el.select("div.name").text().trim()
                    .removePrefix("EP ").toFloatOrNull() ?: 0f
            }
        }.reversed()
    }

    // ======================== Video ========================

    override fun videoListRequest(episode: SEpisode): Request =
        GET(baseUrl + episode.url, headers)

    override fun videoListParse(response: Response): List<Video> {
        val doc = response.asJsoup()
        val videos = mutableListOf<Video>()

        // Get the embed iframe source
        val iframeSrc = doc.select("div.play-video iframe").attr("src")
        if (iframeSrc.isBlank()) return emptyList()

        // Fetch embed page to extract the actual stream URL
        val embedResponse = client.newCall(GET(iframeSrc, headers)).execute()
        val embedDoc = embedResponse.asJsoup()

        // Extract m3u8 / mp4 from script tags
        val scriptData = embedDoc.select("script").map { it.data() }
            .firstOrNull { it.contains("sources") } ?: ""

        val m3u8Regex = Regex("""file\s*:\s*["']([^"']+\.m3u8[^"']*)["']""")
        val mp4Regex = Regex("""file\s*:\s*["']([^"']+\.mp4[^"']*)["']""")

        m3u8Regex.findAll(scriptData).forEach { match ->
            videos.add(Video(match.groupValues[1], "HLS", match.groupValues[1]))
        }
        mp4Regex.findAll(scriptData).forEach { match ->
            videos.add(Video(match.groupValues[1], "MP4", match.groupValues[1]))
        }

        return videos
    }

    // ======================== Helpers ========================

    private fun Response.asJsoup(): Document = Jsoup.parse(body.string())
}
