package app.miyoru.domain.download.model

import kotlinx.serialization.Serializable

@Serializable
data class DownloadRequest(val mediaId: String, val unitId: String?, val kind: String)

@Serializable
data class DownloadTask(val id: String, val state: String)
