package com.redload.app.extractor

import org.json.JSONObject
import org.jsoup.Jsoup

/**
 * Free, self-hosted alternative to the RapidAPI-based TikTok flow.
 *
 * Logic ported (not copy-pasted — reimplemented in our own style using
 * org.json instead of Jackson) from Skraper's TikTokSkraper.kt:
 *   1. Fetch the TikTok page HTML.
 *   2. Read embedded JSON from either <script id="__UNIVERSAL_DATA_FOR_REHYDRATION__">
 *      (current TikTok format) or the older <script id="SIGI_STATE"> — TikTok
 *      has used both over time and may switch back without notice.
 *   3. Pull title/stats/cover out of that JSON for display.
 *   4. Resolve the actual playable file via the generic Open Graph extractor
 *      (og:video meta tag) — same trick Skraper uses, since TikTok's raw
 *      CDN links in the JSON tend to be short-lived/signed per-session.
 *
 * This is a heuristic, best-effort path — it complements (does not replace)
 * the existing tiktok-scraper7 RapidAPI flow already in MainActivity. If
 * TikTok changes its page structure, this may silently fall back to null,
 * and the paid API path remains available as a reliable backup.
 */
class TikTokDirectExtractor : VideoExtractor {

    override val name: String = "TikTokDirect"

    override fun canHandle(url: String): Boolean = "tiktok.com" in url

    override fun extract(url: String): ExtractedVideo? {
        val html = HttpFetcher.fetchHtml(url) ?: return null
        val doc = Jsoup.parse(html, url)

        val itemJson = extractItemJson(doc) ?: return openGraphFallback(url)

        val title = itemJson.optString("desc", "")
        val cover = itemJson.optJSONObject("video")?.optString("cover", "").orEmpty()
        val authorId = itemJson.optJSONObject("author")?.optString("uniqueId")
            ?: itemJson.optString("authorId", "")
        val videoId = itemJson.optString("id", "")

        // The metadata JSON gives us title/cover reliably, but not always a
        // stable playable link — resolve the real file via Open Graph on the
        // canonical page URL, same as Skraper's resolve() step does.
        val canonicalUrl = if (authorId.isNotEmpty() && videoId.isNotEmpty()) {
            "https://www.tiktok.com/@$authorId/video/$videoId"
        } else url

        val resolved = OpenGraphExtractor().extract(canonicalUrl)

        return ExtractedVideo(
            title = title.ifEmpty { resolved?.title.orEmpty() },
            videoUrl = resolved?.videoUrl ?: return null,
            thumbnailUrl = cover.ifEmpty { resolved.thumbnailUrl },
            sourcePlatform = "tiktok"
        )
    }

    private fun openGraphFallback(url: String): ExtractedVideo? =
        OpenGraphExtractor().extract(url)?.copy(sourcePlatform = "tiktok")

    /** Pulls the first post's raw JSON object out of whichever embedded script TikTok used. */
    private fun extractItemJson(doc: org.jsoup.nodes.Document): JSONObject? {
        // Current format: __UNIVERSAL_DATA_FOR_REHYDRATION__ → __DEFAULT_SCOPE__ → ...itemStruct
        doc.getElementById("__UNIVERSAL_DATA_FOR_REHYDRATION__")?.html()?.let { raw ->
            runCatching {
                val scope = JSONObject(raw).optJSONObject("__DEFAULT_SCOPE__") ?: return@let
                // Single-video page structure
                scope.optJSONObject("webapp.video-detail")
                    ?.optJSONObject("itemInfo")
                    ?.optJSONObject("itemStruct")
                    ?.let { return it }
            }
        }

        // Older/alternate format: SIGI_STATE → ItemModule → { "<id>": {...} }
        doc.getElementById("SIGI_STATE")?.html()?.let { raw ->
            runCatching {
                val itemModule = JSONObject(raw).optJSONObject("ItemModule") ?: return@let
                val firstKey = itemModule.keys().asSequence().firstOrNull() ?: return@let
                return itemModule.optJSONObject(firstKey)
            }
        }

        return null
    }
}
