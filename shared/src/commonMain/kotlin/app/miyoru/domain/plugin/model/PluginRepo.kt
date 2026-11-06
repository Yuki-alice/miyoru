package app.miyoru.domain.plugin.model

import kotlinx.serialization.Serializable

/** 已由用户确认信任并落库的插件仓库；[fingerprint] 为身份指纹（仓库 ID + 链接 + 发布者公钥）。 */
@Serializable
data class PluginRepo(
    val id: String,
    val url: String,
    val name: String,
    val description: String? = null,
    val websiteUrl: String? = null,
    val iconUrl: String? = null,
    val schemaVersion: Int,
    val fingerprint: String,
    val enabled: Boolean = true,
    val lastUpdated: Long = 0L,
    val lastError: String? = null,
    val pluginCount: Int = 0,
)

/**
 * `inspectRepository()` 的识别结果。只存在于 Data 层内存，用户确认信任前不落库。
 * [fingerprint] 是身份指纹：SHA-256(repositoryId + 仓库 URL + signingKey)，与索引内容无关；
 * 插件版本更新不改变它，换仓库 ID／链接／发布者公钥才需重新确认，用于防仓库劫持。
 * 索引内容摘要由 `plugin:api` 层单独计算，仅用于诊断展示，Domain 不依赖该类型。
 */
@Serializable
data class RepositoryPreview(
    val url: String,
    val repositoryId: String,
    val name: String,
    val description: String? = null,
    val websiteUrl: String? = null,
    val iconUrl: String? = null,
    val schemaVersion: Int,
    val fingerprint: String,
    val pluginCount: Int,
    val hasSignature: Boolean,
    /** 同 repositoryId 已存在时，本次识别是「身份指纹变化后的重新确认」。 */
    val alreadyAdded: Boolean = false,
)
