package app.miyoru.data.download.hls

import app.miyoru.core.common.AppError

/** 极简 HLS 解析（P0）：媒体播放列表 + 明文分片；加密分片（EXT-X-KEY 非 NONE）拒绝。 */
data class HlsSegment(val uri: String, val durationSecs: Double)

data class HlsMediaPlaylist(val segments: List<HlsSegment>, val targetDurationSecs: Double)

fun parseHlsPlaylist(text: String): HlsMediaPlaylist {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty() || lines.first() != "#EXTM3U") {
        throw AppError.Parse("not a playlist")
    }
    if (lines.any { it.startsWith("#EXT-X-STREAM-INF") }) {
        throw AppError.Parse("master playlist not supported in P0; resolve variant first")
    }
    var target = 0.0
    val segs = mutableListOf<HlsSegment>()
    var pendingDuration = -1.0
    for (line in lines) {
        when {
            line.startsWith("#EXT-X-TARGETDURATION:") ->
                target = line.substringAfter(":").toDoubleOrNull() ?: 0.0
            line.startsWith("#EXT-X-KEY:") && !line.contains("METHOD=NONE") ->
                throw AppError.CapabilityUnsupported("encrypted segments")
            line.startsWith("#EXTINF:") ->
                pendingDuration = line.substringAfter("#EXTINF:").substringBefore(",").toDoubleOrNull() ?: -1.0
            !line.startsWith("#") -> {
                if (pendingDuration < 0) throw AppError.Parse("segment without EXTINF: $line")
                segs.add(HlsSegment(line, pendingDuration))
                pendingDuration = -1.0
            }
        }
    }
    if (segs.isEmpty()) throw AppError.Parse("no segments")
    return HlsMediaPlaylist(segs, target)
}

fun resolveHlsUri(playlistUrl: String, ref: String): String {
    if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
    val base = playlistUrl.substringBefore("?", "").substringBeforeLast("/", "")
    return if (ref.startsWith("/")) {
        val origin = playlistUrl.substringBefore("://") + "://" + playlistUrl.substringAfter("://").substringBefore("/")
        origin + ref
    } else {
        "$base/$ref"
    }
}
