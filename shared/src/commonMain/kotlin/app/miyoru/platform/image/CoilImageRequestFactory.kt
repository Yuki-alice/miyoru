package app.miyoru.platform.image

import coil3.network.NetworkHeaders

class CoilImageRequestFactory : ImageRequestFactory {
    override fun networkHeaders(source: ImageSource): NetworkHeaders {
        if (source.headers.isEmpty()) return NetworkHeaders.EMPTY
        val builder = NetworkHeaders.Builder()
        source.headers.forEach { (k, v) -> builder.add(k, v) }
        return builder.build()
    }
}
