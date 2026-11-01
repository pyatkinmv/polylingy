package com.polylingy.client

import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

/**
 * The pictures of every course, kept locally so courses work offline: `<dir>/<courseId>/images/…` for files from the
 * course archive, `<dir>/<courseId>/remote/<hash>` for pictures downloaded from a URL.
 */
class FileImageStore(private val dir: Path) : ImageStore {
    override fun put(courseId: Uuid, src: String, bytes: ByteArray) {
        val file = file(courseId, src)
        file.parent.createDirectories()
        file.writeBytes(bytes)
    }

    override fun get(courseId: Uuid, src: String): ByteArray? = file(courseId, src).takeIf { it.exists() }?.readBytes()

    override fun deleteCourse(courseId: Uuid) {
        val courseDir = dir.resolve(courseId.toString())
        if (courseDir.toFile().deleteRecursively()) log.info { "Deleted pictures of course $courseId" }
    }

    /** [src] has been checked on import: an `https://` URL or a relative path without `..`. */
    private fun file(courseId: Uuid, src: String): Path {
        val courseDir = dir.resolve(courseId.toString())
        return if (src.startsWith("https://")) courseDir.resolve("remote").resolve(sha256(src)) else courseDir.resolve(src)
    }

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Downloads over HTTPS with a timeout and a size limit. */
class HttpImageFetcher(private val maxBytes: Int = 10 * 1024 * 1024) : ImageFetcher {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build()

    override fun fetch(url: String): ByteArray {
        val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(20)).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        check(response.statusCode() == 200) { "HTTP ${response.statusCode()}" }
        val body = response.body()
        check(body.size <= maxBytes) { "larger than ${maxBytes / 1024 / 1024} MB" }
        return body
    }
}
