package app.miyoru.plugin.api

private val ID_RE = Regex("^[a-z0-9][a-z0-9._-]{1,63}$")
private val VERSION_RE = Regex("^\\d+\\.\\d+\\.\\d+(-[\\w.]+)?$")
private val HEX64_RE = Regex("^[0-9a-fA-F]{64}$")

/** 返回问题列表；为空表示通过。 */
fun PluginManifest.validate(appVersion: String, enforceSignature: Boolean): List<String> {
    val problems = mutableListOf<String>()
    if (!ID_RE.matches(id)) problems += "bad id: $id"
    if (name.isBlank() || name.length > 64) problems += "bad name"
    if (!VERSION_RE.matches(version)) problems += "bad version: $version"
    if (apiVersion !in 1..CURRENT_API_VERSION) problems += "unsupported apiVersion: $apiVersion"
    val minParts = parseSemver(minAppVersion)
    val appParts = parseSemver(appVersion)
    if (minParts == null) problems += "bad minAppVersion: $minAppVersion"
    else if (appParts != null && compareSemver(minParts, appParts) > 0) {
        problems += "minAppVersion $minAppVersion > app $appVersion"
    }
    if (mediaTypes.isEmpty() || !mediaTypes.all { it in PLUGIN_MEDIA_TYPES }) {
        problems += "bad mediaTypes: $mediaTypes"
    }
    if (!capabilities.all { it in PLUGIN_CAPABILITIES }) problems += "bad capabilities: $capabilities"
    if (permissions.domains.isEmpty()) problems += "no domains"
    permissions.domains.forEach { d ->
        if (!isValidHttpsOrigin(d)) problems += "bad domain: $d"
    }
    if (entry.isBlank() || ".." in entry || entry.startsWith("/") || '\\' in entry || !entry.endsWith(".js")) {
        problems += "bad entry: $entry"
    }
    if (entrySha256 != null && !HEX64_RE.matches(entrySha256)) problems += "bad entrySha256"
    if (enforceSignature) {
        if (signature.isNullOrBlank()) problems += "signature required"
        else if (!isPlausibleSignature(signature)) problems += "bad signature"
    } else if (signature != null && !isPlausibleSignature(signature)) {
        problems += "bad signature"
    }
    return problems
}

fun parseSemver(v: String): List<Int>? {
    val m = VERSION_RE.matchEntire(v) ?: return null
    return v.substringBefore("-").split(".").map { it.toInt() }
}

fun compareSemver(a: List<Int>, b: List<Int>): Int {
    for (i in 0 until maxOf(a.size, b.size)) {
        val c = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return 0
}

/** 允许 `https://host` 或 `https://host/prefix`；拒绝非 https、私网字面 IP、localhost。 */
fun isValidHttpsOrigin(raw: String): Boolean {
    val v = raw.trim()
    if (!v.startsWith("https://")) return false
    val rest = v.removePrefix("https://")
    if (rest.isEmpty()) return false
    val host = rest.substringBefore("/").substringBefore(":").lowercase()
    if (host.isEmpty() || "." !in host && host != "localhost") return false
    if (host == "localhost") return false
    if (isPrivateIpLiteral(host)) return false
    if (host.any { it !in 'a'..'z' && it !in '0'..'9' && it != '.' && it != '-' }) return false
    return true
}

fun isPrivateIpLiteral(host: String): Boolean {
    val parts = host.split(".")
    if (parts.size != 4) return false
    val nums = parts.map { it.toIntOrNull() ?: return false }
    if (nums.any { it !in 0..255 }) return false
    return when {
        nums[0] == 10 -> true
        nums[0] == 127 -> true
        nums[0] == 172 && nums[1] in 16..31 -> true
        nums[0] == 192 && nums[1] == 168 -> true
        nums[0] == 169 && nums[1] == 254 -> true
        nums[0] == 0 -> true
        else -> false
    }
}

fun isPlausibleSignature(base64: String): Boolean {
    if (base64.length < 80 || base64.length > 100) return false
    if (base64.any { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' && it != '+' && it != '/' && it != '=' }) return false
    return true
}
