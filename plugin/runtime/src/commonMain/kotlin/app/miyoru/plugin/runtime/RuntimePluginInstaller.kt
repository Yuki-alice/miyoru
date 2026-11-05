package app.miyoru.plugin.runtime

import app.miyoru.plugin.api.InstalledPackage
import app.miyoru.plugin.api.PluginInstallerFacade
import app.miyoru.plugin.api.PluginPackageSource
import app.miyoru.plugin.api.PackageEntry
import app.miyoru.plugin.api.ZipReader
import app.miyoru.plugin.runtime.install.PluginFileSystem
import app.miyoru.plugin.runtime.install.PluginInstaller
import app.miyoru.plugin.runtime.install.PluginPackageValidator
import app.miyoru.plugin.runtime.install.PluginLayout
import app.miyoru.plugin.runtime.install.PluginSignatureVerifier
import app.miyoru.plugin.runtime.install.Sha256

/**
 * facade 实现：zip 解包由平台侧 ZipReader 注入（JVM=java.util.zip，各端自备），
 * 其余流程全复用 common 安装器。
 */
class RuntimePluginInstaller(
    private val fs: PluginFileSystem,
    private val appVersion: String,
    private val defaultEnforceSignature: Boolean,
    private val signatureVerifier: PluginSignatureVerifier? = null,
    private val zipReader: ZipReader,
) : PluginInstallerFacade {
    override suspend fun install(pkg: PluginPackageSource, enforceSignature: Boolean): InstalledPackage {
        val validator = PluginPackageValidator(appVersion, enforceSignature, signatureVerifier)
        val result = PluginInstaller(fs, validator).install(zipReader.read(pkg.bytes))
        return InstalledPackage(result.manifest, Sha256.hex(pkg.bytes), result.hadPrevious)
    }

    override suspend fun rollback(pluginId: String) {
        PluginInstaller(fs, PluginPackageValidator(appVersion, false, signatureVerifier)).rollback(pluginId)
    }

    override suspend fun uninstall(pluginId: String) {
        PluginInstaller(fs, PluginPackageValidator(appVersion, false, signatureVerifier)).uninstall(pluginId)
    }

    override fun installDir(pluginId: String): String = PluginLayout.pluginDir(pluginId)
}
