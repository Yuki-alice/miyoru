package app.miyoru.domain.source.model

import app.miyoru.core.model.MediaType
import app.miyoru.core.model.SourceId
import kotlinx.serialization.Serializable

@Serializable
data class SourceInfo(
    val id: SourceId,
    val name: String,
    val mediaTypes: List<MediaType> = emptyList(),
    val capabilities: List<String> = emptyList(),
    val enabled: Boolean = true,
)
