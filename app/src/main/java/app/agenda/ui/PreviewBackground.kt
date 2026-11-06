package app.agenda.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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

/** The three-dots menu in the top bar: preview background image (pick or clear) and About. */
@Composable
internal fun MoreMenu(bg: PreviewBackground) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { bg.pick(it) }
    var open by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text("Preview background image") },
            onClick = {
                open = false
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
        if (bg.image != null) {
            DropdownMenuItem(
                text = { Text("Remove background image") },
                onClick = {
                    open = false
                    bg.reset()
                },
            )
        }
        DropdownMenuItem(
            text = { Text("About") },
            onClick = {
                open = false
                about = true
            },
        )
    }
    if (about) {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        AlertDialog(
            onDismissRequest = { about = false },
            confirmButton = { TextButton(onClick = { about = false }) { Text("OK") } },
            title = { Text("Agenda") },
            text = { Text("Version $version") },
        )
    }
}
