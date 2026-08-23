package com.redload.app.extractor

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Free, login-free Instagram extractor — our own implementation, not part
 * of the Skraper library.
 *
 * Public Instagram posts (Reels/Posts) embed a JSON-LD block
 * (<script type="application/ld+json">) in the page for link-preview /
 * SEO purposes, containing a "video" object with a direct "contentUrl".
 * This is the same category of technique as TikTokDirectExtractor: read
 * metadata Instagram already publishes in the page itself, no private API,
 * no login, no session cookies.
 *
 * If the JSON-LD block is missing/changed (Instagram changes markup often,
 * more so than TikTok), we fall back to the generic Open Graph extractor
 * (og:video), which still works for many public posts.
 */
class InstagramDirectExtractor : VideoExtractor {

    override val name: String = "InstagramDirect"

    override fun canHandle(url: String): Boolean = "instagram.com" in url

    override fun extract(url: String): ExtractedVideo? {
        val html = HttpFetcher.fetchHtml(url) ?: return openGraphFallback(url)
        val doc = Jsoup.parse(html, url)

        fromJsonLd(doc)?.let { return it }
        return openGraphFallback(url)
    }

    private fun openGraphFallback(url: String): ExtractedVideo? =
        OpenGraphExtractor().extract(url)?.copy(sourcePlatform = "instagram")

    /**
     * Instagram post pages can include one or more
     * <script type="application/ld+json"> blocks. We scan all of them for
     * one that looks like a VideoObject and has a contentUrl.
     */
    private fun fromJsonLd(doc: Document): ExtractedVideo? {
        val scripts = doc.select("script[type=application/ld+json]")

        for (script in scripts) {
            val raw = script.html().trim()
            if (raw.isEmpty()) continue

            val result = runCatching { parseLdJsonBlock(raw) }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    private fun parseLdJsonBlock(raw: String): ExtractedVideo? {
        // A block can be a single object or an array of objects.
        val candidates: List<JSONObject> = when {
            raw.trimStart().startsWith("[") -> {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            }
            else -> listOf(JSONObject(raw))
        }

        for (obj in candidates) {
            val videoUrl = obj.optString("contentUrl", "")
                .ifEmpty { obj.optJSONObject("video")?.optString("contentUrl", "") ?: "" }

            if (videoUrl.isNotEmpty()) {
                val title = obj.optString("caption", obj.optString("name", ""))
                val thumbnail = obj.optString("thumbnailUrl", "")
                return ExtractedVideo(
                    title = title,
                    videoUrl = videoUrl,
                    thumbnailUrl = thumbnail,
                    sourcePlatform = "instagram"
                )
            }
        }
        return null
    }
}
