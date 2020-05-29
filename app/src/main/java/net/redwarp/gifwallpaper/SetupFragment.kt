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
package net.redwarp.gifwallpaper

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import dev.sasikanth.colorsheet.ColorSheet
import kotlinx.android.synthetic.main.activity_setup.*
import kotlinx.android.synthetic.main.fragment_setup.*
import net.redwarp.gifwallpaper.data.ColorScheme
import net.redwarp.gifwallpaper.data.Model
import net.redwarp.gifwallpaper.data.WallpaperStatus
import net.redwarp.gifwallpaper.renderer.RenderCallback
import net.redwarp.gifwallpaper.renderer.Renderer
import net.redwarp.gifwallpaper.renderer.RendererMapper
import net.redwarp.gifwallpaper.renderer.WallpaperRenderer
import net.redwarp.gifwallpaper.util.isDark

const val PICK_GIF_FILE = 2

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SetupFragment : Fragment() {
    private var renderCallback: RenderCallback? = null
    private var currentScale = 0
    private var currentRotation = 0
    private lateinit var model: Model
    private var colorInfo: ColorScheme? = null
        set(value) {
            field = value
            change_color_button.isEnabled = value != null
        }
    private var currentColor: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_setup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        open_gif_button.setOnClickListener {
            pickDocument()
        }
        change_scale_button.setOnClickListener {
            changeScale()
        }
        change_color_button.setOnClickListener {
            changeColor()
        }
        rotate_button.setOnClickListener {
            rotate()
        }

        renderCallback =
            RenderCallback(surface_view.holder, Looper.getMainLooper()).also(lifecycle::addObserver)

        model = Model.get(requireContext())
        RendererMapper(
            model = model,
            surfaceHolder = surface_view.holder,
            animated = true,
            unsetText = getString(R.string.click_the_open_gif_button)
        ).observe(
            viewLifecycleOwner,
            Observer { renderer: Renderer ->
                renderCallback?.renderer = renderer
            })

        model.colorInfoData.observe(viewLifecycleOwner, Observer { colorStatus ->
            colorInfo = colorStatus as? ColorScheme
            change_color_button.isEnabled = colorStatus is ColorScheme
        })
        model.backgroundColorData.observe(viewLifecycleOwner, Observer {
            currentColor = it
            adjustTheme(it)
        })
        model.scaleTypeData.observe(viewLifecycleOwner, Observer {
            currentScale = it.ordinal
        })
        model.rotationData.observe(viewLifecycleOwner, Observer {
            currentRotation = it.ordinal
        })
        model.wallpaperStatus.observe(viewLifecycleOwner, Observer {
            val isWallpaperSet = it is WallpaperStatus.Wallpaper
            change_scale_button.isEnabled = isWallpaperSet
            rotate_button.isEnabled = isWallpaperSet
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        renderCallback?.let(lifecycle::removeObserver)
        renderCallback = null
    }

    private fun adjustTheme(backgroundColor: Int) {
        setStatusBarColor(backgroundColor.isDark())
        val overflowColor = if (backgroundColor.isDark()) Color.WHITE else Color.BLACK

        val icon = activity?.toolbar?.overflowIcon?.let {
            DrawableCompat.wrap(it).also { wrapped -> wrapped.setTint(overflowColor) }
        }

        activity?.toolbar?.overflowIcon = icon
    }

    private fun setStatusBarColor(isDark: Boolean) {
        activity?.window?.apply {
            if (isDark) {
                decorView.systemUiVisibility =
                    decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                decorView.systemUiVisibility =
                    decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }

    private fun pickDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/gif"
        }

        startActivityForResult(intent, PICK_GIF_FILE)
    }

    private fun changeScale() {
        currentScale = (currentScale + 1) % WallpaperRenderer.ScaleType.values().size
        model.setScaleType(WallpaperRenderer.ScaleType.values()[currentScale])
    }

    private fun changeColor() {
        colorInfo?.let { colorInfo ->
            val colors =
                colorInfo.palette.targets.map {
                    colorInfo.palette.getColorForTarget(it, Color.BLACK)
                }.distinct().toIntArray()

            ColorSheet().colorPicker(
                colors, currentColor, noColorOption = true
            ) {
                if (it == ColorSheet.NO_COLOR) {
                    currentColor = null
                    model.setBackgroundColor(colorInfo.defaultColor)
                } else {
                    currentColor = it
                    model.setBackgroundColor(it)
                }
            }.cornerRadius(0).show(parentFragmentManager)
        }
    }

    private fun rotate() {
        currentRotation = (currentRotation + 1) % WallpaperRenderer.Rotation.values().size
        model.setRotation(WallpaperRenderer.Rotation.values()[currentRotation])
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_GIF_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                model.loadNewGif(uri)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.extras, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.clear_gif -> {
                model.clearGif()
                return true
            }
            R.id.about -> {
                startActivity(TextActivity.getIntent(requireContext(), "about.md"))

                return true
            }
            R.id.privacy -> {
                startActivity(TextActivity.getIntent(requireContext(), "privacy.md"))

                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }
}
