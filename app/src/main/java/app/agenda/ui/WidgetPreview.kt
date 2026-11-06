package app.agenda.ui

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.ScrollView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.children
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.runComposition
import app.agenda.domain.AgendaSettings
import app.agenda.widget.AgendaWidget
import app.agenda.widget.PREVIEW_ROWS

/** Renders the real widget (not a copy) by running its Glance composition into a RemoteViews. */
@OptIn(ExperimentalGlanceApi::class)
@Composable
internal fun WidgetPreview(
    settings: AgendaSettings,
    wallpaper: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Needs no permission (API 27+): the wallpaper's own palette stands in when the image itself is unreadable.
    // Dimmed toward a dark base so a loud palette can't wash out the widget's light text.
    val backdrop = remember { wallpaperGradient(context).map { lerp(it, DIM, 0.7f) } }
    val views by produceState<RemoteViews?>(null, settings) {
        AgendaWidget(preview = true).runComposition(context, AppWidgetId(PREVIEW_ID)).collect { value = it }
    }
    Box(modifier.fillMaxWidth().height(PREVIEW_HEIGHT.dp).clip(RoundedCornerShape(settings.cornerRadius.dp))) {
        wallpaper?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Box(Modifier.fillMaxSize().background(Brush.linearGradient(backdrop)))
        AndroidView(
            factory = { FrameLayout(it) },
            update = { host ->
                host.removeAllViews()
                views?.let { host.addView(it.apply(context, host).also(::disableClicks).also(::scrollRows)) }
            },
            modifier = Modifier.fillMaxSize().clipToBounds(),
        )
    }
}

/** Like the real widget list, only the rows scroll; the header and background stay put. */
internal fun scrollRows(root: View) {
    val found = ArrayList<View>()
    root.findViewsWithText(found, PREVIEW_ROWS, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
    val rows = found.firstOrNull() ?: return
    val parent = rows.parent as ViewGroup
    val index = parent.indexOfChild(rows)
    val params = rows.layoutParams
    parent.removeView(rows)
    val scroll =
        ScrollView(rows.context).apply {
            isVerticalScrollBarEnabled = false
            addView(rows, FrameLayout.LayoutParams(MATCH, WRAP))
        }
    parent.addView(scroll, index, params)
}

/** The preview only shows the widget: drop its tap actions so it can be scrolled without leaving the app. */
internal fun disableClicks(view: View) {
    view.setOnClickListener(null)
    view.isClickable = false
    (view as? ViewGroup)?.children?.forEach(::disableClicks)
}

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

private const val PREVIEW_ID = -1

private const val PREVIEW_HEIGHT = 260

private val DIM = Color(0xFF14110F)

private val FALLBACK_BACKDROP = listOf(Color(0xFF6A8CAF), Color(0xFFE0A96D))

internal fun wallpaperGradient(context: Context): List<Color> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return FALLBACK_BACKDROP
    val manager = WallpaperManager.getInstance(context)
    val c = manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return FALLBACK_BACKDROP
    return listOfNotNull(c.primaryColor, c.secondaryColor, c.tertiaryColor).map { Color(it.toArgb()) }
}
