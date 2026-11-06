package app.agenda.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * What the settings preview shows behind the widget, so transparency looks like it will on the home screen.
 * An image the user picked (no permission needed, the photo picker hands over just that one file), else null and
 * the preview paints a gradient. Reading the live wallpaper is not offered: on Android 13+ it takes two storage
 * grants, one of them all-files access.
 */
internal class PreviewBackground(private val context: Context) {
    private val file = File(context.filesDir, "preview_bg.jpg")

    var image by mutableStateOf<ImageBitmap?>(null)
        private set

    fun reload() {
        image = if (file.exists()) BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null
    }

    fun pick(uri: Uri?) {
        val stream = uri?.let(context.contentResolver::openInputStream) ?: return
        val bitmap = stream.use(BitmapFactory::decodeStream) ?: return
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        reload()
    }

    fun reset() {
        file.delete()
        reload()
    }
}

private const val JPEG_QUALITY = 90

@Composable
internal fun PreviewBackgroundRows(bg: PreviewBackground) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { bg.pick(it) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Optional. Show your own wallpaper behind the preview so transparency looks right. " +
                "Pick a screenshot of your home screen; no permission needed.",
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            ) { Text("Choose an image") }
            if (bg.image != null) OutlinedButton(onClick = bg::reset) { Text("Reset") }
        }
    }
}
