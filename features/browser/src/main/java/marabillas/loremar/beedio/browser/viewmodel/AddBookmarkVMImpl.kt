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

package marabillas.loremar.beedio.browser.viewmodel

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import marabillas.loremar.beedio.base.mvvm.ActionLiveData

class AddBookmarkVMImpl : AddBookmarkVM() {
    private val currentPageData = MutableLiveData<CurrentPageData>()
    private val openBookmarkerAction = ActionLiveData()

    override fun openBookmarker(data: CurrentPageData) {
        currentPageData.value = data
        openBookmarkerAction.go()
    }

    override fun observeOpenBookmarker(lifecycleOwner: LifecycleOwner, observer: Observer<Any>) {
        openBookmarkerAction.observe(lifecycleOwner, observer)
    }

    override fun observeCurrentPageData(lifecycleOwner: LifecycleOwner, observer: Observer<CurrentPageData>) {
        currentPageData.observe(lifecycleOwner, observer)
    }
}