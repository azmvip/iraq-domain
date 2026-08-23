package com.redload.app.extractor

import org.jsoup.Jsoup

/**
 * Generic, platform-agnostic extractor based on Open Graph / Twitter Card
 * meta tags (og:video, og:video:secure_url, twitter:player:stream, og:image).
 *
 * This is the same technique Skraper's `fetchOpenGraphMedia()` uses as its
 * universal fallback — most sites that embed shareable video already expose
 * these tags for link-preview purposes (WhatsApp/Telegram/Twitter previews
 * rely on the same metadata), so it works across many platforms with zero
 * platform-specific code.
 *
 * Because it's generic, register it LAST in the registry — more specific
 * extractors (that know how to get the real no-watermark / HD source) should
 * get a chance to handle the URL first.
 */
class OpenGraphExtractor : VideoExtractor {

    override val name: String = "OpenGraph"

    override fun canHandle(url: String): Boolean = url.startsWith("http")

    override fun extract(url: String): ExtractedVideo? {
        val html = HttpFetcher.fetchHtml(url) ?: return null
        val doc = Jsoup.parse(html, url)

        val videoUrl = metaContent(doc, "og:video:secure_url")
            ?: metaContent(doc, "og:video:url")
            ?: metaContent(doc, "og:video")
            ?: metaContent(doc, "twitter:player:stream")
            ?: return null // nothing we can play/download — genuinely unsupported page

        val title = metaContent(doc, "og:title") ?: doc.title()
        val thumbnail = metaContent(doc, "og:image").orEmpty()

        return ExtractedVideo(
            title = title,
            videoUrl = videoUrl,
            thumbnailUrl = thumbnail,
            sourcePlatform = "generic"
        )
    }

    private fun metaContent(doc: org.jsoup.nodes.Document, property: String): String? {
        val el = doc.selectFirst("meta[property=$property]")
            ?: doc.selectFirst("meta[name=$property]")
        return el?.attr("content")?.takeIf { it.isNotBlank() }
    }
}
