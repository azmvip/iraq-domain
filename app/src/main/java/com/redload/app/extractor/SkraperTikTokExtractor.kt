package com.redload.app.extractor

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import ru.sokomishalov.skraper.client.ktor.KtorClientDsl
import ru.sokomishalov.skraper.client.ktor.KtorSkraperClient
import ru.sokomishalov.skraper.model.URLString
import ru.sokomishalov.skraper.provider.tiktok.TikTokSkraper

/**
 * Wraps the full Skraper library (ru.sokomishalov.skraper:skrapers:0.13.0)
 * behind our own VideoExtractor interface, so it plugs straight into
 * VideoExtractorLibrary alongside our lightweight hand-written extractors.
 *
 * ⚠️ Built from Skraper's public README/source patterns, not verified against
 * a live compile in this environment (no Android SDK / network access here).
 * The exact method name on TikTokSkraper (getPosts / getPost / resolve) may
 * differ slightly between versions — if the build fails on this file
 * specifically, open TikTokSkraper.kt from the downloaded dependency
 * (Android Studio/AIDE can jump to its decompiled source) and adjust the
 * call below to match the real method signature.
 *
 * Runs Skraper's suspend/Flow-based API via runBlocking — safe here because
 * this class, like the rest of the extractor library, is only ever called
 * from a background thread, never the UI thread.
 */
class SkraperTikTokExtractor : VideoExtractor {

    override val name: String = "SkraperTikTok"

    private val client by lazy { KtorSkraperClient() }
    private val skraper by lazy { TikTokSkraper(client = client) }

    override fun canHandle(url: String): Boolean = "tiktok.com" in url

    override fun extract(url: String): ExtractedVideo? = runBlocking {
        runCatching {
            val post = skraper.getPosts(URLString(url)).firstOrNull() ?: return@runBlocking null
            val media = post.media.firstOrNull() ?: return@runBlocking null
            val resolvedUrl = skraper.resolve(media)

            ExtractedVideo(
                title = post.text.orEmpty(),
                videoUrl = resolvedUrl,
                thumbnailUrl = "", // Skraper's Post model may expose a separate
                                    // thumbnail field depending on version —
                                    // left blank rather than guessed.
                sourcePlatform = "tiktok-skraper"
            )
        }.getOrNull()
    }
}
