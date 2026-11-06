package app.miyoru.domain.plugin.model

/** 插件目录中单个条目的可展示状态。 */
enum class CatalogEntryState {
    /** 仓库中可安装，本地未安装。 */
    AVAILABLE,

    /** 已安装且版本一致。 */
    INSTALLED,

    /** 来源仓库提供了更新版本。 */
    UPDATE_AVAILABLE,

    /** 当前应用版本或插件 apiVersion 不支持。 */
    INCOMPATIBLE,

    /** 本地已由另一个仓库安装，禁止跨仓库覆盖。 */
    SOURCE_MISMATCH,

    /** 仓库已停用。 */
    REPOSITORY_DISABLED,
}
