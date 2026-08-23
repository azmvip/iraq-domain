package com.redload.app.extractor

/**
 * Result of a successful extraction — everything the UI/downloader needs.
 */
data class ExtractedVideo(
    val title: String = "",
    val videoUrl: String,
    val thumbnailUrl: String = "",
    val audioUrl: String? = null,
    val sourcePlatform: String = ""
)

/**
 * Contract every platform-specific (or generic) extractor implements.
 * Mirrors the "Skraper" design pattern: canHandle() decides ownership of a
 * URL, extract() does the actual work. All extractors run on a background
 * thread — never call these from the main thread.
 */
interface VideoExtractor {
    val name: String
    fun canHandle(url: String): Boolean
    fun extract(url: String): ExtractedVideo?
}
