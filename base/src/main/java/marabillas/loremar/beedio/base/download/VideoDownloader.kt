/*
 * Beedio is an Android app for downloading videos
 * Copyright (C) 2019 Loremar Marabillas
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package marabillas.loremar.beedio.base.download

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.*
import marabillas.loremar.beedio.base.database.DownloadItem
import marabillas.loremar.beedio.base.web.HttpNetwork
import java.io.*
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.util.concurrent.Executors


class VideoDownloader(private val context: Context) {
    private val http = HttpNetwork()
    private val progressNotifyingDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var progressNotifyingJob: Job? = null
    private lateinit var notifier: DownloadNotifier
    private var isStopped = false

    fun download(item: DownloadItem) {
        isStopped = false
        try {
            notifier = DownloadNotifier(context, item, prepareTargetDirectory())
            progressNotifyingJob?.cancel()
            progressNotifyingJob = CoroutineScope(progressNotifyingDispatcher).launch {
                while (!isStopped) {
                    notifier.notifyProgress()
                    delay(1000)
                }
            }

            val isChunked = item.isChunked
            val audioUrl = item.audioUrl

            if (!audioUrl.isNullOrBlank())
                downloadVideoAudio(item)
            else if (isChunked)
                downloadChunkedVideo(item)
            else
                downloadDefault(item)

            progressNotifyingJob?.cancel()
            if (!isStopped)
                notifier.notifyFinish()
        } catch (e: DownloadException) {
            progressNotifyingJob?.cancel()
            notifier.notifyFinish()
            throw e
        }
    }

    private fun downloadVideoAudio(item: DownloadItem) {
        TODO()
    }

    private fun downloadChunkedVideo(item: DownloadItem) {
        try {
            val name = item.name
            val ext = item.ext
            val targetFilename = "$name.$ext"
            val targetDirectory = prepareTargetDirectory().apply {
                if (!exists() && !mkdir() && !createNewFile())
                    throw DownloadException("unavailable target directory")
            }

            val progressFile = File(context.cacheDir, "$name.dat")
            val videoFile = File(targetDirectory, targetFilename)
            var totalChunks = 0L
            if (progressFile.exists()) {
                val input = FileInputStream(progressFile)
                val data = DataInputStream(input)
                totalChunks = data.readLong()
                data.close()
                input.close()
                if (!videoFile.exists()) {
                    if (!videoFile.createNewFile()) {
                        throw DownloadException("Can't create file for download.")
                    }
                }
            } else if (videoFile.exists()) {
                return
            } else {
                if (!videoFile.createNewFile()) {
                    throw DownloadException("Can't create file for download.")
                }
                if (!progressFile.createNewFile()) {
                    throw DownloadException("Can't create progress file.")
                }
            }

            if (videoFile.exists() && progressFile.exists()) {
                while (!isStopped) {
                    val website = item.sourceWebsite
                    var chunkUrl: String? = null
                    when (website) {
                        "dailymotion.com" -> chunkUrl = getNextChunkWithDailymotionRule(item.videoUrl, totalChunks)
                        "vimeo.com" -> chunkUrl = getNextChunkWithVimeoRule(item.videoUrl, totalChunks)
                        "twitter.com" -> chunkUrl = getNextChunkWithM3U8Rule(item, totalChunks)
                        "metacafe.com" -> chunkUrl = getNextChunkWithM3U8Rule(item, totalChunks)
                        "myspace.com" -> chunkUrl = getNextChunkWithM3U8Rule(item, totalChunks)
                    }
                    if (chunkUrl == null) {
                        if (!progressFile.delete()) {
                            TODO("Log can't delete progressFile")
                        }
                        return
                    }
                    val bytesOfChunk = ByteArrayOutputStream()
                    try {
                        val conn = http.open(chunkUrl)
                        conn.stream?.let { inputStream ->
                            val readChannel = Channels.newChannel(inputStream)
                            val writeChannel = Channels.newChannel(bytesOfChunk)
                            var read: Int
                            while (!isStopped) {
                                val buffer = ByteBuffer.allocateDirect(1024)
                                read = readChannel.read(buffer)
                                if (read != -1) {
                                    buffer.flip()
                                    writeChannel.write(buffer)
                                } else {
                                    val vAddChunk = FileOutputStream(videoFile, true)
                                    vAddChunk.write(bytesOfChunk.toByteArray())
                                    val outputStream = FileOutputStream(progressFile, false)
                                    val dataOutputStream = DataOutputStream(outputStream)
                                    dataOutputStream.writeLong(++totalChunks)
                                    dataOutputStream.close()
                                    outputStream.close()
                                    break
                                }
                            }
                            readChannel.close()
                            conn.stream?.close()
                            bytesOfChunk.close()
                        }
                    } catch (e: FileNotFoundException) {
                        if (!progressFile.delete()) {
                            TODO("Log can't delete progressFile")
                        }
                        return
                    } catch (e: IOException) {
                        throw DownloadException("IOException - ${e.message}")
                    }
                }
            }
        } catch (e: IOException) {
            throw DownloadException("IOException - ${e.message}", e)
        } catch (e: Exception) {
            throw DownloadException(e.message ?: "Exception on chunked download", e)
        }
    }

    private fun getNextChunkWithDailymotionRule(url: String, totalChunks: Long): String? {
        return url.replace("FRAGMENT".toRegex(), "frag(${totalChunks + 1})")
    }

    private fun getNextChunkWithVimeoRule(url: String, totalChunks: Long): String? {
        return url.replace("SEGMENT".toRegex(), "segment-${totalChunks + 1}")
    }

    private fun getNextChunkWithM3U8Rule(item: DownloadItem, totalChunks: Long): String? {
        val url = item.videoUrl
        val website = item.sourceWebsite

        var line: String? = null
        try {
            val m3u8Con = http.open(url)
            m3u8Con.stream?.bufferedReader()?.apply {
                while (!isStopped) {
                    line = readLine()
                    if (line == null)
                        break

                    if ((website == "twitter.com" || website == "myspace.com") && line!!.endsWith(".ts")) {
                        break
                    } else if (website == "metacafe.com" && line!!.endsWith(".mp4")) {
                        break
                    }
                }
                if (line != null) {
                    var l: Long = 1
                    while (l < totalChunks + 1 && !isStopped) {
                        readLine()
                        line = readLine()
                        l++
                    }
                }
                close()
            }
            m3u8Con.stream?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return if (line != null) {
            val prefix: String
            when (website) {
                "twitter.com" -> "https://video.twimg.com$line"
                "metacafe.com" -> {
                    prefix = url.substring(0, url.lastIndexOf("/") + 1)
                    prefix + line
                }
                "myspace.com" -> {
                    prefix = url.substring(0, url.lastIndexOf("/") + 1)
                    prefix + line
                }
                else -> null
            }
        } else {
            null
        }
    }

    private fun downloadDefault(item: DownloadItem) {
        try {
            val url = item.videoUrl
            val name = item.name
            val ext = item.ext
            val totalSize = item.size

            val targetFilename = "$name.$ext"
            val targetDirectory = prepareTargetDirectory().apply {
                if (!exists() && !mkdir() && !createNewFile())
                    throw DownloadException("unavailable target directory")
            }

            val downloadFile = File(targetDirectory, targetFilename)
            val conn: HttpNetwork.Connection
            val out = if (downloadFile.exists()) {
                val headers = mapOf("Range" to "bytes=${downloadFile.length()}-")
                conn = http.open(url, headers)
                FileOutputStream(downloadFile.absolutePath, true)
            } else {
                conn = http.open(url)
                if (downloadFile.createNewFile())
                    FileOutputStream(downloadFile.absolutePath, true)
                else
                    throw DownloadException("can not create download file")
            }

            conn.stream?.let { inputStream ->
                if (downloadFile.exists()) {
                    val readChannel = Channels.newChannel(inputStream)
                    val fileChannel = out.channel
                    while (downloadFile.length() < totalSize && !isStopped) {
                        fileChannel.transferFrom(readChannel, 0, 1024)
                    }
                    readChannel.close()
                    out.flush()
                    out.close()
                    fileChannel.close()
                } else
                    throw DownloadException("no download file")
            }
        } catch (e: FileNotFoundException) {
            throw DownloadException(null, UnavailableException())
        } catch (e: IOException) {
            throw DownloadException("IOException - ${e.message}", e)
        } catch (e: Exception) {
            throw DownloadException(e.message ?: "Exception on default download", e)
        }
    }

    private fun prepareTargetDirectory(): File {
        val downloadFolder = getDownloadFolder(context)
        if (downloadFolder != null) return downloadFolder

        val message = getUnavailableDownloadFolderMessage(Environment.getExternalStorageState())
        throw DownloadException(message)
    }

    fun stop() {
        isStopped = true
        progressNotifyingJob?.cancel()
        notifier.stop()
    }

    inner class DownloadException(message: String? = null, e: Throwable? = null)
        : Exception(message?.let { "DownloadException: $it" }, e)

    inner class UnavailableException : Exception()

    companion object {
        fun getDownloadFolder(context: Context): File? {
            val downloadFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (
                    downloadFolder != null
                    && (downloadFolder.exists() || downloadFolder.mkdir() || downloadFolder.createNewFile())
                    && downloadFolder.canWrite()
            ) {
                return downloadFolder
            }

            val externalStorage = Environment.getExternalStorageDirectory()
            val externalStorageState = Environment.getExternalStorageState()
            if (
                    externalStorage != null
                    && (externalStorage.exists() || externalStorage.mkdir() || externalStorage.createNewFile())
                    && externalStorage.canWrite()
                    && externalStorageState == Environment.MEDIA_MOUNTED
            ) {
                return File(externalStorage, "Download")
            }

            val appExternal = context.getExternalFilesDir(null)
            if (
                    appExternal != null
                    && (appExternal.exists() || appExternal.mkdir() || appExternal.createNewFile())
                    && appExternal.canWrite()
            ) {
                return File(appExternal, "Download")
            }

            return null
        }

        fun getUnavailableDownloadFolderMessage(externalStorageState: String): String {
            return when (externalStorageState) {
                Environment.MEDIA_UNMOUNTABLE -> "External storage is un-mountable."
                Environment.MEDIA_SHARED -> "USB mass storage is turned on. Can not mount external storage."
                Environment.MEDIA_UNMOUNTED -> "External storage is not mounted."
                Environment.MEDIA_MOUNTED_READ_ONLY -> "External storage is mounted but has no write access."
                Environment.MEDIA_BAD_REMOVAL -> "External storage was removed without being properly ejected."
                Environment.MEDIA_REMOVED -> "External storage does not exist. Probably removed."
                Environment.MEDIA_NOFS -> "External storage is blank or has unsupported filesystem."
                Environment.MEDIA_CHECKING -> "Still checking for external storage."
                Environment.MEDIA_EJECTING -> "External storage is currently being ejected."
                Environment.MEDIA_UNKNOWN -> "External storage is not available for some unknown reason."
                Environment.MEDIA_MOUNTED -> "External storage is mounted but for some unknown reason is not" +
                        " available."
                else -> "External storage is not available. No reason."
            }
        }
    }
}