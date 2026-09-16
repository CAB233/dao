package win.zuoye.dao.update

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import win.zuoye.dao.data.UpdateChannel

private const val latestReleaseUrl = "https://api.github.com/repos/CAB233/dao/releases/latest"

@Immutable
data class UpdateInfo(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val sha256: String?,
)

@Serializable
private data class ReleaseResponse(
    @SerialName("tag_name") val tagName: String,
    val body: String = "",
    val assets: List<ReleaseAsset> = emptyList(),
)

@Serializable
private data class ReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val digest: String? = null,
)

interface UpdateChecker {
    suspend fun checkForUpdate(currentVersionName: String, channel: UpdateChannel): UpdateInfo?
}

object AppUpdater : UpdateChecker {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun checkForUpdate(
        currentVersionName: String,
        channel: UpdateChannel,
    ): UpdateInfo? = withContext(Dispatchers.IO) {
        val response = requestText(channel.resolve(latestReleaseUrl))
        val release = json.decodeFromString<ReleaseResponse>(response)
        val versionName = release.tagName.removePrefix("v").removePrefix("V")
        if (!isNewerVersion(versionName, currentVersionName)) return@withContext null

        val asset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: throw IOException("The latest release does not contain an APK")
        UpdateInfo(
            versionName = versionName,
            releaseNotes = release.body.trim(),
            downloadUrl = channel.resolve(asset.browserDownloadUrl),
            sha256 = asset.digest
                ?.takeIf { it.startsWith("sha256:", ignoreCase = true) }
                ?.substringAfter(':'),
        )
    }

    suspend fun downloadApk(
        context: Context,
        update: UpdateInfo,
        onProgress: suspend (Int?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, "Dao-${update.versionName}.apk")
        val temporary = File(directory, "Dao-${update.versionName}.download")
        val connection = openConnection(update.downloadUrl)
        try {
            val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
            val digest = MessageDigest.getInstance("SHA-256")
            var copiedBytes = 0L
            var lastProgress: Int? = null
            connection.inputStream.buffered().use { input ->
                FileOutputStream(temporary).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        copiedBytes += count
                        val progress = totalBytes?.let { ((copiedBytes * 100L) / it).toInt().coerceAtMost(100) }
                        if (progress != lastProgress) {
                            lastProgress = progress
                            withContext(Dispatchers.Main.immediate) { onProgress(progress) }
                        }
                    }
                }
            }
            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (update.sha256 != null && !actualSha256.equals(update.sha256, ignoreCase = true)) {
                temporary.delete()
                throw IOException("Downloaded APK checksum does not match the release")
            }
            if (target.exists() && !target.delete()) {
                throw IOException("Could not replace the cached APK")
            }
            if (!temporary.renameTo(target)) {
                throw IOException("Could not finish the APK download")
            }
            target
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    fun installApk(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    private fun requestText(url: String): String {
        val connection = openConnection(url)
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Dao-Android")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connect()
            val statusCode = responseCode
            if (statusCode !in 200..299) {
                disconnect()
                throw IOException("Update server returned HTTP $statusCode")
            }
        }
}

internal fun isNewerVersion(candidate: String, current: String): Boolean {
    fun components(value: String): List<Int> = value
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-')
        .substringBefore('+')
        .split('.')
        .map { it.toIntOrNull() ?: return emptyList() }

    val candidateParts = components(candidate)
    val currentParts = components(current)
    if (candidateParts.isEmpty() || currentParts.isEmpty()) return false
    repeat(maxOf(candidateParts.size, currentParts.size)) { index ->
        val comparison = candidateParts.getOrElse(index) { 0 }
            .compareTo(currentParts.getOrElse(index) { 0 })
        if (comparison != 0) return comparison > 0
    }
    return false
}

private fun UpdateChannel.resolve(url: String): String = urlPrefix + url
