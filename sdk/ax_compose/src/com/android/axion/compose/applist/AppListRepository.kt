/*
 * Copyright (C) 2025-2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.axion.compose.applist

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AppFilter {
    USER_ONLY,
    SYSTEM_ONLY,
    ALL,
    NO_OVERLAYS,
    LAUNCHABLE_ONLY,
    /** Also list the apps of the clone (dual app) profiles of the current user. */
    INCLUDE_DUAL_APPS,
}

data class AppEntry(
    val packageName: String,
    val className: String,
    val label: String,
    val icon: Drawable,
    val isSystem: Boolean,
    val userId: Int = 0,
    val isClone: Boolean = false,
)

@Composable
fun rememberAppList(vararg filters: AppFilter): State<List<AppEntry>> {
    val context = LocalContext.current
    val pm = context.packageManager
    val filterSet = filters.toSet()
    val state = remember(filterSet) { mutableStateOf<List<AppEntry>>(emptyList()) }

    LaunchedEffect(filterSet) {
        withContext(Dispatchers.IO) {
            val selfId = UserHandle.myUserId()
            val users = buildList {
                add(selfId to false)
                if (AppFilter.INCLUDE_DUAL_APPS in filterSet) {
                    getCloneProfileIds(context, selfId).forEach { add(it to true) }
                }
            }

            val entries = users.flatMap { (userId, isClone) ->
                val apps = if (userId == selfId) {
                    pm.getInstalledApplications(PackageManager.GET_META_DATA)
                } else {
                    runCatching {
                        pm.getInstalledApplicationsAsUser(PackageManager.GET_META_DATA, userId)
                    }.getOrNull() ?: emptyList()
                }
                apps.mapNotNull { info ->
                    val isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0

                    if (AppFilter.USER_ONLY in filterSet && isSystem) return@mapNotNull null
                    if (AppFilter.SYSTEM_ONLY in filterSet && !isSystem) return@mapNotNull null
                    if (AppFilter.NO_OVERLAYS in filterSet && info.isResourceOverlay) return@mapNotNull null

                    val className = getLaunchClassName(pm, info.packageName, userId, selfId)
                    if (AppFilter.LAUNCHABLE_ONLY in filterSet && className == null) {
                        return@mapNotNull null
                    }

                    val icon = runCatching { info.loadIcon(pm) }.getOrNull() ?: return@mapNotNull null
                    AppEntry(
                        packageName = info.packageName,
                        className = className ?: "",
                        label = info.loadLabel(pm).toString(),
                        icon = icon,
                        isSystem = isSystem,
                        userId = userId,
                        isClone = isClone,
                    )
                }
            }.sortedBy { it.label.lowercase() }

            state.value = entries
        }
    }

    return state
}

private fun getCloneProfileIds(context: Context, selfId: Int): List<Int> {
    return runCatching {
        context.getSystemService(UserManager::class.java)
            ?.getProfiles(selfId)
            ?.filter { it.id != selfId && it.isCloneProfile }
            ?.map { it.id }
    }.getOrNull() ?: emptyList()
}

/** The launcher activity class of [packageName] for [userId], or null if it has none. */
private fun getLaunchClassName(
    pm: PackageManager,
    packageName: String,
    userId: Int,
    selfId: Int,
): String? {
    if (userId == selfId) {
        return pm.getLaunchIntentForPackage(packageName)?.component?.className
    }
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setPackage(packageName)
    return runCatching {
        pm.queryIntentActivitiesAsUser(intent, 0, userId).firstOrNull()?.activityInfo?.name
    }.getOrNull()
}

@Composable
fun rememberFilteredAppList(query: String, vararg filters: AppFilter): State<List<AppEntry>> {
    val allApps = rememberAppList(*filters)
    return remember(query, allApps) {
        derivedStateOf {
            if (query.isBlank()) {
                allApps.value
            } else {
                val lower = query.lowercase()
                allApps.value.filter { entry ->
                    entry.label.lowercase().contains(lower) ||
                        entry.packageName.lowercase().contains(lower)
                }
            }
        }
    }
}
