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

package marabillas.loremar.beedio.browser.fragment

import android.content.DialogInterface.BUTTON_POSITIVE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.android.support.DaggerFragment
import marabillas.loremar.beedio.base.database.BookmarksSQLite
import marabillas.loremar.beedio.base.extensions.bottomAppBar
import marabillas.loremar.beedio.base.extensions.recyclerView
import marabillas.loremar.beedio.base.extensions.textView
import marabillas.loremar.beedio.base.extensions.toolbar
import marabillas.loremar.beedio.browser.R
import marabillas.loremar.beedio.browser.adapters.AddBookmarkAdapter
import marabillas.loremar.beedio.browser.viewmodel.AddBookmarkVM
import javax.inject.Inject

class AddBookmarkFragment @Inject constructor() : DaggerFragment(), Toolbar.OnMenuItemClickListener {
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var addBookmarkAdapter: AddBookmarkAdapter

    @Inject
    lateinit var bookmarksSQLite: BookmarksSQLite

    private lateinit var addBookmarkVM: AddBookmarkVM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.add_bookmark, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activity?.let {
            addBookmarkVM = ViewModelProvider(it, viewModelFactory).get(AddBookmarkVM::class.java)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.bottomAppBar(R.id.add_bookmark_bottom_appbar)
                .replaceMenu(R.menu.add_bookmark_menu)

        view.recyclerView(R.id.recycler_add_bookmark).apply {
            adapter = addBookmarkAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    override fun onStart() {
        super.onStart()
        addBookmarkVM.observeCurrentPageData(this, Observer {
            textView(R.id.add_bookmark_title_value)?.text = it.title
            textView(R.id.add_bookmark_url_value)?.text = it.url
            textView(R.id.add_bookmark_dest_value)?.text = BookmarksSQLite.ROOT_FOLDER
        })

        updateFolders()

        requireView().bottomAppBar(R.id.add_bookmark_bottom_appbar)
                .setOnMenuItemClickListener(this)

        toolbar(R.id.add_bookmark_header)?.setNavigationOnClickListener {
            parentFragmentManager.beginTransaction()
                    .remove(this)
                    .commit()
        }
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.add_bookmark_menu_new_folder -> showCreateNewFolderDialog()
        }
        return true
    }

    private fun updateFolders() {
        val cursor = bookmarksSQLite.folders
        val folders = mutableListOf<String>()
        if (bookmarksSQLite.currentTable != BookmarksSQLite.ROOT_FOLDER)
            folders.add("...")
        while (cursor.moveToNext()) {
            folders.add(cursor.getString(cursor.getColumnIndex("title")))
        }
        cursor.close()
        addBookmarkAdapter.folders = folders
    }

    private fun showCreateNewFolderDialog() {
        val editText = EditText(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            hint = "Enter folder name"
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(resources.getString(R.string.create_new_folder))
                .setView(editText)
                .setPositiveButton("Create", null)
                .setNegativeButton("Cancel", null)
                .create()

        dialog.setOnShowListener {
            (it as AlertDialog).getButton(BUTTON_POSITIVE)
                    .setOnClickListener {
                        if (editText.text.isNullOrBlank())
                            editText.error = "Folder name must not be blank"
                        else {
                            bookmarksSQLite.addFolder(editText.text.toString())
                            dialog.dismiss()
                            updateFolders()
                        }
                    }
        }

        dialog.show()
    }
}