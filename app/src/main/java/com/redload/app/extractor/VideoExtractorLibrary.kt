package com.redload.app.extractor

/**
 * Entry point for the whole library. Usage:
 *
 *   Thread {
 *       val result = VideoExtractorLibrary.extract(url)
 *       runOnUiThread { ...update UI with result... }
 *   }.start()
 *
 * Always call extract() from a background thread — it performs blocking
 * network I/O.
 */
object VideoExtractorLibrary {

    // Order matters: specific extractors (that know how to get the best
    // quality / no-watermark source) are tried first. OpenGraphExtractor is
    // the universal catch-all and stays last since it "handles" any http(s)
    // URL but only succeeds if the page actually exposes video meta tags.
    private val extractors: List<VideoExtractor> = listOf(
        SkraperTikTokExtractor(),   // full Skraper library — tried first for TikTok
        TikTokDirectExtractor(),    // our lightweight free fallback for TikTok
        InstagramDirectExtractor(), // our own Instagram extractor (JSON-LD based)
        OpenGraphExtractor()        // universal last-resort fallback
    )

    fun extract(url: String): ExtractedVideo? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null

        for (extractor in extractors) {
            if (extractor.canHandle(trimmed)) {
                val result = runCatching { extractor.extract(trimmed) }.getOrNull()
                if (result != null) return result
                // If this extractor claimed the URL but failed, keep trying
                // the remaining ones instead of giving up immediately.
            }
        }
        return null
    }
}
