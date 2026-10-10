package com.example.camerax

import android.content.ClipData
import android.content.ClipDescription
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.camerax.ui.theme.CameraXTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(SCANNER_MODE_FULL)
            .setPageLimit(3)
            .setGalleryImportAllowed(false)
            .setResultFormats(RESULT_FORMAT_JPEG)
            .build()

        val scanner = GmsDocumentScanning.getClient(options)

        setContent {
            CameraXTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var scannedImageUri by remember {
                        mutableStateOf<List<Uri>>(emptyList())
                    }
                    val scannerLauncher =
                        rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.StartIntentSenderForResult(),
                            onResult = {
                                if (it.resultCode == RESULT_OK) {
                                    val scanningResult =
                                        GmsDocumentScanningResult.fromActivityResultIntent(it.data)

                                    scanningResult?.pages?.let { pages ->
                                        for (page in pages) {
                                            scannedImageUri = listOf(pages[0].imageUri)

                                        }
                                    }
                                    scanningResult?.pdf?.let { pdf ->
                                        val fos =FileOutputStream(File(filesDir,"scan.pdf"))
                                        contentResolver.openInputStream(pdf.uri)?.use {
                                            it.copyTo(fos)
                                        }
                                        val pdfUri = pdf.uri
                                        val pageCount = pdf.pageCount
                                    }


                                }
                            }
                        )

                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        scannedImageUri.forEach { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth()
                            )

                        }
                        Button(onClick = {
                            scanner.getStartScanIntent(this@MainActivity)
                                .addOnSuccessListener { intentSender ->
                                    scannerLauncher.launch(
                                        IntentSenderRequest.Builder(intentSender).build()
                                    )
                                }
                                .addOnFailureListener {
                                    Toast.makeText(
                                        applicationContext,
                                        it.message,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }) {
                            Text(text = stringResource(id = R.string.scan))

                        }

                        if (scannedImageUri.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(
                                    8.dp,
                                    Alignment.CenterHorizontally
                                )
                            ) {
                                Button(onClick = {
                                    val uri = scannedImageUri.first()
                                    scope.launch {
                                        val saved = saveImageToGallery(context, uri)
                                        Toast.makeText(
                                            context,
                                            if (saved) R.string.saved_to_gallery
                                            else R.string.save_failed,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }) {
                                    Text(text = stringResource(id = R.string.save_to_gallery))
                                }

                                OutlinedButton(onClick = {
                                    val uri = scannedImageUri.first()
                                    scope.launch {
                                        val bitmap = withContext(Dispatchers.IO) {
                                            loadBitmapFromUri(context, uri)
                                        }
                                        val copied = bitmap != null &&
                                                withContext(Dispatchers.Main) {
                                                    copyBitmapToClipboard(
                                                        context,
                                                        bitmap!!,
                                                        "Scanned document"
                                                    )
                                                }
                                        Toast.makeText(
                                            context,
                                            if (copied) R.string.copied_to_clipboard
                                            else R.string.copy_failed,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }) {
                                    Text(text = stringResource(id = R.string.copy_to_clipboard))
                                }
                            }
                        }

                    }
                }

            }
        }
    }
}

/**
 * Copies the image at [uri] (the scanned page produced by ML Kit, which lives in the app's
 * private cache) into the device gallery via MediaStore. No WRITE_EXTERNAL_STORAGE permission
 * is required on API 29+; on older devices the legacy READ/WRITE permissions are needed.
 */
private suspend fun saveImageToGallery(context: Context, uri: Uri): Boolean =
    withContext(Dispatchers.IO) {
        val filename = "SCAN_${System.currentTimeMillis()}.jpg"
        var outputStream: FileOutputStream? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/Scans"
                    )
                }
                val imageCollection = MediaStore.Images.Media
                    .getContentUri("external_primary")
                val insertedUri = context.contentResolver.insert(imageCollection, values)
                    ?: return@withContext false
                outputStream = context.contentResolver.openOutputStream(insertedUri)
                    as? FileOutputStream ?: return@withContext false
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES
                )
                val scansDir = File(picturesDir, "Scans")
                if (!scansDir.exists() && !scansDir.mkdirs()) return@withContext false
                legacyFile = File(scansDir, filename)
                outputStream = FileOutputStream(legacyFile)
            }

            context.contentResolver.openInputStream(uri)?.use { input ->
                input.copyTo(outputStream!!)
            } ?: return@withContext false

            outputStream.flush()

            legacyFile?.let { savedFile ->
                // Make the new image visible in the gallery on legacy storage.
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(savedFile.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            outputStream?.close()
        }
    }

/**
 * Decodes the image at [uri] into a Bitmap that can be placed on the clipboard.
 */
private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Places [bitmap] on the system clipboard so it can be pasted as an image in other apps.
 * On API 24+ the bitmap is written directly to the clipboard; on older versions it is
 * exposed through the app's FileProvider cache directory first.
 */
private fun copyBitmapToClipboard(context: Context, bitmap: Bitmap, label: String): Boolean {
    return try {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val clip = ClipData.newBitmapItem(label, bitmap)
            clipboard.setPrimaryClip(clip)
        } else {
            // Pre-N devices do not support bitmap clip items, so share a content URI instead.
            val file = File(context.cacheDir, "clipboard_scan.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.setReadable(true, false)
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val clip = ClipData(
                label,
                arrayOf(ClipDescription.MIMETYPE_TEXT_IMAGE),
                ClipData.Item(contentUri)
            )
            clipboard.setPrimaryClip(clip)
        }
        true
    } catch (e: Exception) {
        false
    }
}




