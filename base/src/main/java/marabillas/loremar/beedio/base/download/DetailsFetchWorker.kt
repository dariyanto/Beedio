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
import androidx.room.Room
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.GsonBuilder
import marabillas.loremar.beedio.base.database.DownloadListDatabase
import marabillas.loremar.beedio.base.download.DownloadQueueManager.Companion.AUDIO_DETAILS_FILE
import marabillas.loremar.beedio.base.download.DownloadQueueManager.Companion.VIDEO_DETAILS_FILE
import marabillas.loremar.beedio.base.media.VideoDetails
import marabillas.loremar.beedio.base.media.VideoDetailsFetcher
import marabillas.loremar.beedio.base.media.VideoDetailsTypeAdapter
import java.io.File
import java.util.concurrent.CountDownLatch

class DetailsFetchWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        const val HAS_VIDEO_DETAILS = "has_video_details"
        const val HAS_AUDIO_DETAILS = "has_audio_details"

        fun deleteDetailsFiles(context: Context) {
            DownloadQueueManager.deleteData(context, VIDEO_DETAILS_FILE)
            DownloadQueueManager.deleteData(context, AUDIO_DETAILS_FILE)
        }
    }

    private val downloadList = Room
            .databaseBuilder(
                    context,
                    DownloadListDatabase::class.java,
                    "downloads"
            )
            .build()
            .downloadListDao()

    private val videoDetailsFetcher = VideoDetailsFetcher()
    private val gson = GsonBuilder()
            .registerTypeAdapter(VideoDetails::class.java, VideoDetailsTypeAdapter())
            .create()

    override fun doWork(): Result {
        DownloadQueueManager.state.postValue(DownloadQueueManager.State.FETCHING_DETAILS)
        deleteDetailsFiles(applicationContext)
        val first = downloadList.first() ?: return Result.failure().apply {
            DownloadQueueManager.state.postValue(DownloadQueueManager.State.INACTIVE)
        }

        var data = workDataOf(
                HAS_VIDEO_DETAILS to false,
                HAS_AUDIO_DETAILS to false
        )
        val fetchCdLatch = CountDownLatch(1)

        videoDetailsFetcher.fetchDetails(first.videoUrl, object : VideoDetailsFetcher.FetchListener {
            override fun onUnFetched(error: Throwable) {
                fetchCdLatch.countDown()
            }

            override fun onFetched(details: VideoDetails) {
                details.saveVideoDetails()

                if (first.audioUrl != null)
                    videoDetailsFetcher.fetchDetails(first.audioUrl, object : VideoDetailsFetcher.FetchListener {
                        override fun onUnFetched(error: Throwable) {
                            data = workDataOf(
                                    HAS_VIDEO_DETAILS to true,
                                    HAS_AUDIO_DETAILS to false
                            )
                            fetchCdLatch.countDown()
                        }

                        override fun onFetched(details: VideoDetails) {
                            details.saveAudioDetails()
                            data = workDataOf(
                                    HAS_VIDEO_DETAILS to true,
                                    HAS_AUDIO_DETAILS to true
                            )
                            fetchCdLatch.countDown()
                        }
                    })
                else {
                    data = workDataOf(
                            HAS_VIDEO_DETAILS to true,
                            HAS_AUDIO_DETAILS to false
                    )
                    fetchCdLatch.countDown()
                }
            }
        })

        fetchCdLatch.await()

        return Result.success(data)
    }

    private fun VideoDetails.saveVideoDetails() {
        val json = gson.toJson(this)
        File(applicationContext.filesDir, VIDEO_DETAILS_FILE).writeText(json)
    }

    private fun VideoDetails.saveAudioDetails() {
        val json = gson.toJson(this)
        File(applicationContext.filesDir, AUDIO_DETAILS_FILE).writeText(json)
    }

    override fun onStopped() {
        super.onStopped()
        videoDetailsFetcher.cancel()
    }
}