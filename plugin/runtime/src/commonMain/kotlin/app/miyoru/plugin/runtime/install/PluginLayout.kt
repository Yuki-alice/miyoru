package app.miyoru.plugin.runtime.install

/** 安装布局（相对安装根目录）：plugins/<id>/…、暂存区 .staging/<id>、回滚备份 .backup/<id>。 */
object PluginLayout {
    fun pluginDir(pluginId: String) = "plugins/$pluginId"
    fun stagingDir(pluginId: String) = ".staging/$pluginId"
    fun backupDir(pluginId: String) = ".backup/$pluginId"
}
