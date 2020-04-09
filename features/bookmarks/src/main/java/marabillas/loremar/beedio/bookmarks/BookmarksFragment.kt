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

package marabillas.loremar.beedio.bookmarks

import android.database.Cursor
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.android.support.DaggerFragment
import marabillas.loremar.beedio.base.database.BookmarksSQLite
import marabillas.loremar.beedio.base.extensions.recyclerView
import marabillas.loremar.beedio.base.extensions.toolbar
import marabillas.loremar.beedio.base.mvvm.MainViewModel
import javax.inject.Inject

class BookmarksFragment @Inject constructor() : DaggerFragment() {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var bookmarksAdapter: BookmarksAdapter

    private lateinit var mainViewModel: MainViewModel
    private lateinit var bookmarksSQLite: BookmarksSQLite

    private val toolbar; get() = toolbar(R.id.bookmarks_toolbar)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bookmarks, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activity?.let {
            mainViewModel = ViewModelProvider(it::getViewModelStore, viewModelFactory).get(MainViewModel::class.java)
            bookmarksSQLite = BookmarksSQLite(it)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.bookmarks_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.recyclerView(R.id.bookmarks_recyclerview).apply {
            adapter = bookmarksAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    override fun onStart() {
        super.onStart()
        (activity as AppCompatActivity?)?.setSupportActionBar(toolbar)
        toolbar?.setNavigationOnClickListener { mainViewModel.setIsNavDrawerOpen(true) }

        loadBookmarksData()
    }

    private fun loadBookmarksData() {
        val bookmarks = mutableListOf<BookmarksItem>()
        if (bookmarksSQLite.currentTable != BookmarksSQLite.ROOT_FOLDER) {
            val b = BookmarksItem(
                    type = "upFolder",
                    icon = resources.getDrawable(R.drawable.ic_folder_yellow_24dp),
                    title = "..."
            )
            bookmarks.add(b)
        }
        val cursor: Cursor = bookmarksSQLite.bookmarks
        while (cursor.moveToNext()) {
            val type = cursor.getString(cursor.getColumnIndex("type"))
            val title = cursor.getString(cursor.getColumnIndex("title"))
            val icon: Drawable
            var url: String? = null
            if (type == "folder") {
                icon = resources.getDrawable(R.drawable.ic_folder_yellow_24dp)
            } else {
                val iconInBytes = cursor.getBlob(cursor.getColumnIndex("icon"))
                icon = if (iconInBytes != null) {
                    val iconBitmap = BitmapFactory.decodeByteArray(iconInBytes, 0, iconInBytes.size)
                    BitmapDrawable(resources, iconBitmap)
                } else {
                    resources.getDrawable(R.drawable.ic_bookmark_border_24dp)
                }
                url = cursor.getString(cursor.getColumnIndex("link"))
            }
            val b = BookmarksItem(
                    type = type,
                    icon = icon,
                    title = title,
                    url = url
            )
            bookmarks.add(b)
        }
        cursor.close()
        bookmarksAdapter.bookmarks = bookmarks
    }
}