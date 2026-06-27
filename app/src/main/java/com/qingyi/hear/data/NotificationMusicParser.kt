package com.qingyi.hear.data

import com.qingyi.hear.domain.MusicInfo
import com.qingyi.hear.domain.MusicSource

/**
 * Pure parser that maps music notification fields to [MusicInfo].
 *
 * The parser only consumes already-extracted text fields so it can be tested
 * without Android framework classes.
 */
object NotificationMusicParser {

    fun parse(
        packageName: String,
        appName: String,
        title: String?,
        text: String?,
        subText: String?,
        bigText: String?,
        postTime: Long,
        isPlaying: Boolean = true,
    ): MusicInfo? {
        val normalizedTitle = firstNotBlank(title, text, subText, bigText) ?: return null
        val normalizedArtist = when {
            !text.isNullOrBlank() && text != normalizedTitle -> text
            !subText.isNullOrBlank() && subText != normalizedTitle -> subText
            !bigText.isNullOrBlank() && bigText != normalizedTitle -> bigText
            else -> ""
        }
        val album = when {
            !subText.isNullOrBlank() && subText != normalizedArtist -> subText
            !bigText.isNullOrBlank() && bigText != normalizedArtist -> bigText
            else -> null
        }

        return MusicInfo(
            title = normalizedTitle,
            artist = normalizedArtist,
            album = album,
            appPackage = packageName,
            appName = appName,
            isPlaying = isPlaying,
            source = MusicSource.fromPackage(packageName),
            timestamp = postTime,
        )
    }

    private fun firstNotBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()
}
