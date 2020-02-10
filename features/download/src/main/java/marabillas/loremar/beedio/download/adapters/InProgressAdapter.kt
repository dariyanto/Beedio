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

package marabillas.loremar.beedio.download.adapters

import android.annotation.SuppressLint
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import marabillas.loremar.beedio.base.media.VideoDetails
import marabillas.loremar.beedio.download.R
import marabillas.loremar.beedio.download.viewmodels.InProgressVM

class InProgressAdapter : RecyclerView.Adapter<InProgressAdapter.InProgressViewHolder>() {
    var eventListener: EventListener? = null
    var isFetching = false
        set(value) {
            field = value
            if (itemCount > 0)
                notifyItemChanged(0)
        }

    private var downloads = listOf<InProgressVM.InProgressItem>()
    private var topItemDetails: VideoDetails? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InProgressViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val id = if (viewType == 0)
            R.layout.download_item_in_progress
        else
            R.layout.download_item_in_queue
        val view = inflater.inflate(id, parent, false)
        return InProgressViewHolder(view)
    }

    override fun getItemCount(): Int = downloads.count()

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) 0 else 1
    }

    override fun onBindViewHolder(holder: InProgressViewHolder, position: Int) {
        holder.bind(downloads[position])
    }

    fun loadData(downloads: List<InProgressVM.InProgressItem>) {
        this.downloads = downloads
        topItemDetails = null
        notifyDataSetChanged()
    }

    fun loadDetails(details: VideoDetails, isAudio: Boolean = false) {
        topItemDetails = details
        if (downloads.count() > 0)
            notifyItemChanged(0)
    }

    fun updateProgress(progress: Int?, downloaded: String) {
        if (downloads.count() > 0) {
            downloads[0].apply {
                this.progress = progress
                inProgressDownloaded = downloaded
            }
            notifyItemChanged(0)
        }
    }

    inner class InProgressViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        private val thumbnail by lazy { findImageView(R.id.item_in_progress_thumbnail) }
        private val progressBar by lazy { findProgressBar(R.id.item_in_progress_progressbar) }
        private val progressPercent by lazy { findTextView(R.id.item_in_progress_progress_percent) }
        private val inProgressTitle by lazy { findTextView(R.id.item_in_progress_title) }
        private val inProgressDownloaded by lazy { findTextView(R.id.item_in_progress_downloaded) }
        private val inProgressDetails by lazy { findTextView(R.id.item_in_progress_details) }
        private val progressBarIndeterminate by lazy {
            findProgressBar(R.id.item_in_progress_progressbar_indeterminate)
        }

        private val inQueueTitle by lazy { findTextView(R.id.item_in_queue_title) }
        private val inQueueDownloaded by lazy { findTextView(R.id.item_in_queue_downloaded) }

        @SuppressLint("SetTextI18n")
        fun bind(item: InProgressVM.InProgressItem) {
            val type = getItemViewType(adapterPosition)
            if (type == 0) {
                if (item.progress != null && !isFetching) {
                    showDeterminateProgress()
                    progressBar.apply {
                        item.progress?.let {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                setProgress(it, true)
                            } else
                                progress = it
                            progressPercent.text = "${item.progress}%"
                        }
                    }
                } else {
                    showIndeterminateProgress()
                    progressPercent.text = ""
                }
                inProgressTitle.text = item.title
                inProgressDownloaded.text = item.inProgressDownloaded

                when {
                    isFetching -> inProgressDetails.text = "Fetching details..."
                    topItemDetails == null -> inProgressDetails.text = "No details"
                    else -> inProgressDetails.text = topItemDetails?.buildDetailsText()
                            ?: "No details"
                }
            } else {
                inQueueTitle.text = item.title
                inQueueDownloaded.text = item.inQueueDownloaded
            }
        }

        private fun showDeterminateProgress() {
            progressBar.visibility = VISIBLE
            progressPercent.isVisible = true
            progressBarIndeterminate.isVisible = false
        }

        private fun showIndeterminateProgress() {
            progressBar.visibility = INVISIBLE
            progressPercent.isVisible = false
            progressBarIndeterminate.isVisible = true

            if (Build.VERSION.SDK_INT < 21) {
                val drawable = ResourcesCompat.getDrawable(itemView.resources, R.drawable.circular_progress, null)
                progressBarIndeterminate.indeterminateDrawable = drawable
            }
        }

        private fun VideoDetails.buildDetailsText(): Spannable {
            return SpannableStringBuilder().apply {
                appendDetail("Filename: ", filename)
                appendDetail("Title: ", title)
                appendDetail("vcodec: ", vcodec ?: "none")
                appendDetail("acodec: ", acodec ?: "none")
                appendDetail("Duration: ", duration)
                appendDetail("Filesize: ", filesize)
                appendDetail("Width: ", width)
                appendDetail("Height: ", height)
                appendDetail("Bitrate: ", bitrate)
                appendDetail("Framerate: ", framerate)
                appendDetail("Encoder: ", encoder)
                appendDetail("Encoded By: ", encodedBy)
                appendDetail("Date: ", date)
                appendDetail("Creation Time: ", creationTime)
                appendDetail("Artist: ", artist)
                appendDetail("Album: ", album)
                appendDetail("Album Artist: ", albumArtist)
                appendDetail("Track: ", track)
                appendDetail("Genre: ", genre)
                appendDetail("Composer: ", composer)
                appendDetail("Performer: ", performer)
                appendDetail("Copyright: ", copyright)
                appendDetail("Publisher: ", publisher)
                appendDetail("Language: ", language)
            }
        }

        private fun SpannableStringBuilder.appendDetail(entryLabel: String, entryValue: String?) {
            entryValue?.let {
                append(SpannableString(entryLabel).style()).appendln(it)
            }
        }

        private fun Spannable.style(): Spannable {
            val color = ResourcesCompat.getColor(itemView.resources, R.color.yellow, null)
            val span = ForegroundColorSpan(color)
            setSpan(span, 0, length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            return this
        }

        private fun findTextView(id: Int) = itemView.findViewById<TextView>(id)
        private fun findImageView(id: Int) = itemView.findViewById<ImageView>(id)
        private fun findProgressBar(id: Int) = itemView.findViewById<ProgressBar>(id)
    }

    interface EventListener {

    }
}