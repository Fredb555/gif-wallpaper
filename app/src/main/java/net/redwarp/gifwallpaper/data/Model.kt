/* Copyright 2020 Redwarp
 * Copyright 2020 GifWallpaper Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.redwarp.gifwallpaper.data

import android.content.Context
import android.graphics.Color
import android.net.Uri
import androidx.annotation.ColorInt
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import net.redwarp.gifwallpaper.renderer.WallpaperRenderer

internal const val SHARED_PREF_NAME = "wallpaper_pref"
internal const val KEY_WALLPAPER_URI = "wallpaper_uri"
internal const val KEY_WALLPAPER_SCALE_TYPE = "wallpaper_scale_type"
internal const val KEY_WALLPAPER_BACKGROUND_COLOR = "wallpaper_background_color"
internal const val KEY_WALLPAPER_ROTATION = "wallpaper_rotation"

class Model private constructor(val context: Context) {
    private val _wallpaperStatus = WallpaperLiveData(context)
    private val _scaleTypeData = ScaleTypeData(context)
    private val _rotationData = RotationData(context)
    private val _backgroundColorData = MediatorLiveData<Int>()

    val wallpaperStatus: LiveData<WallpaperStatus> get() = _wallpaperStatus
    val scaleTypeData: LiveData<WallpaperRenderer.ScaleType> get() = _scaleTypeData
    val rotationData: LiveData<WallpaperRenderer.Rotation> get() = _rotationData
    val colorInfoData: LiveData<ColorInfo> = ColorLiveData(context, wallpaperStatus)
    val backgroundColorData: LiveData<Int> get() = _backgroundColorData

    private var isColorSet = true

    init {
        _backgroundColorData.addSource(colorInfoData) { colorInfo ->
            if (!isColorSet) {
                when (colorInfo) {
                    is ColorScheme -> {
                        _backgroundColorData.postValue(colorInfo.defaultColor)
                        isColorSet = true
                    }
                }
            }
        }
        _backgroundColorData.value = loadBackgroundColor(context)
        _backgroundColorData.observeForever { backgroundColor ->
            storeBackgroundColor(context, backgroundColor)
        }
        wallpaperStatus.observeForever {
            if (it is WallpaperStatus.NotSet) isColorSet = false
        }
    }

    private fun storeBackgroundColor(context: Context, backgroundColor: Int) {
        val sharedPreferences =
            context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putInt(KEY_WALLPAPER_BACKGROUND_COLOR, backgroundColor).apply()
    }

    private fun loadBackgroundColor(context: Context): Int {
        val sharedPreferences =
            context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getInt(KEY_WALLPAPER_BACKGROUND_COLOR, Color.RED)
    }

    fun loadNewGif(uri: Uri) {
        isColorSet = false
        _wallpaperStatus.loadNewGif(uri)
    }

    fun clearGif() {
        isColorSet = false
        _wallpaperStatus.clearGif()
        setBackgroundColor(Color.BLACK)
        setScaleType(WallpaperRenderer.ScaleType.FIT_CENTER)
        setRotation(WallpaperRenderer.Rotation.NORTH)
    }

    fun setScaleType(scaleType: WallpaperRenderer.ScaleType) {
        _scaleTypeData.setScaleType(scaleType)
    }

    fun setRotation(rotation: WallpaperRenderer.Rotation) {
        _rotationData.setRotation(rotation)
    }

    fun setBackgroundColor(@ColorInt color: Int) {
        _backgroundColorData.postValue(color)
    }

    private class ScaleTypeData(private val context: Context) :
        LiveData<WallpaperRenderer.ScaleType>() {
        init {
            loadInitialValue()
        }

        private fun loadInitialValue() {
            postValue(loadCurrentScaleType(context))
        }

        private fun loadCurrentScaleType(context: Context): WallpaperRenderer.ScaleType {
            val sharedPreferences =
                context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)
            val scaleTypeOrdinal = sharedPreferences.getInt(KEY_WALLPAPER_SCALE_TYPE, 0)
            return WallpaperRenderer.ScaleType.values()[scaleTypeOrdinal]
        }

        private fun storeCurrentScaleType(
            context: Context,
            scaleType: WallpaperRenderer.ScaleType
        ) {
            val sharedPreferences =
                context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)
            sharedPreferences.edit().putInt(KEY_WALLPAPER_SCALE_TYPE, scaleType.ordinal).apply()
        }

        fun setScaleType(scaleType: WallpaperRenderer.ScaleType) {
            storeCurrentScaleType(context, scaleType)
            postValue(scaleType)
        }
    }

    private class RotationData(private val context: Context) :
        LiveData<WallpaperRenderer.Rotation>() {
        init {
            postValue(loadCurrentRotation(context))
        }

        private fun loadCurrentRotation(context: Context): WallpaperRenderer.Rotation {
            val sharedPreferences =
                context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)
            val rotationOrdinal = sharedPreferences.getInt(KEY_WALLPAPER_ROTATION, 0)
            return WallpaperRenderer.Rotation.values()[rotationOrdinal]
        }

        private fun storeCurrentRotation(
            context: Context,
            rotation: WallpaperRenderer.Rotation
        ) {
            val sharedPreferences =
                context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)
            sharedPreferences.edit().putInt(KEY_WALLPAPER_ROTATION, rotation.ordinal).apply()
        }

        fun setRotation(rotation: WallpaperRenderer.Rotation) {
            storeCurrentRotation(context, rotation)
            postValue(rotation)
        }
    }

    companion object {
        private lateinit var instance: Model

        fun get(context: Context): Model {
            instance =
                if (Companion::instance.isInitialized) instance else (Model(
                    context.applicationContext
                ))

            return instance
        }
    }
}
