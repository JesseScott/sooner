package tt.co.jesses.sooner.scan

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Reads the words on a photo with ML Kit Text Recognition v2, using the Latin
 * model bundled in the APK, so it works offline straight after install.
 */
class StopSignReader {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun read(image: InputImage): List<OcrWord> = suspendCancellableCoroutine { cont ->
        recognizer.process(image)
            .addOnSuccessListener { text ->
                val words = text.textBlocks.flatMap { block ->
                    block.lines.flatMap { line ->
                        line.elements.mapNotNull { element ->
                            val box = element.boundingBox ?: return@mapNotNull null
                            OcrWord(element.text, box.left, box.top, box.right, box.bottom)
                        }
                    }
                }
                cont.resume(words)
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    suspend fun read(photo: CapturedPhoto): List<OcrWord> =
        read(InputImage.fromBitmap(photo.bitmap, photo.rotationDegrees))

    suspend fun read(context: Context, uri: Uri): List<OcrWord> = read(InputImage.fromFilePath(context, uri))

    fun close() = recognizer.close()
}
