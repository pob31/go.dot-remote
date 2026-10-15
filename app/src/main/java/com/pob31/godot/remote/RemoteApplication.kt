/*
    This file is part of Go.dot remote — https://github.com/pob31/go.dot-remote
    Copyright (C) 2026 Pierre-Olivier Boulant
    Go.dot remote is free software: you can redistribute it and/or modify it under the
    terms of the GNU General Public License as published by the Free Software
    Foundation, either version 3 of the License, or (at your option) any later
    version. Go.dot remote is distributed in the hope that it will be useful, but
    WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
    or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
    (LICENSE, at the repository root) for more details.
    SPDX-License-Identifier: GPL-3.0-or-later
*/

package com.pob31.godot.remote

import android.app.Application
import com.pob31.godot.remote.localization.LocalizationManager

/**
 * The words are loaded once per process, before the activity or the link service
 * needs them: a service restarted by the system on its own has no activity to do it.
 */
class RemoteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LocalizationManager.init(this)
    }
}
