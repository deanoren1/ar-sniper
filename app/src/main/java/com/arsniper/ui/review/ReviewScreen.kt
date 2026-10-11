package com.arsniper.ui.review

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsniper.data.PhotoRepository
import com.arsniper.ml.PersonMask
import com.arsniper.ml.PersonSegmenter
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class ReviewResult(
    val photo: ImageBitmap,
    val overlay: ImageBitmap,
    val coveragePercent: Int
)

/**
 * מסך בדיקה אחרי הצילום: מציג את התמונה ומסמן באדום את מה שזוהה כאנשים.
 * בשלב 9ב נחליף את הסימון בהסרה אמיתית.
 */
@Composable
fun ReviewScreen(
    onContinue: () -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<ReviewResult?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val outcome = withContext(Dispatchers.Default) {
            runCatching { analyze(context) }
        }
        outcome
            .onSuccess { result = it }
            .onFailure { errorText = it.message ?: it.toString() }
    }

    val current = result

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F14))
    ) {
        if (current != null) {
            Image(
                bitmap = current.photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Image(
                bitmap = current.overlay,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        val statusText = when {
            errorText != null -> "Analysis failed: $errorText"
            current == null -> "Analyzing photo..."
            current.coveragePercent == 0 -> "No people detected"
            else -> "People detected: ${current.coveragePercent}% of the photo (red)"
        }

        Text(
            text = statusText,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        if (current == null && errorText == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Button(onClick = onRetake) { Text("RETAKE") }
            Button(onClick = onContinue) { Text("CONTINUE") }
        }
    }
}

private fun analyze(context: Context): ReviewResult {
    val bitmap = PhotoRepository.load(context) ?: error("No photo found")
    val segmenter = PersonSegmenter(context)
    try {
        val mask = segmenter.detectPeople(bitmap)
        return ReviewResult(
            photo = bitmap.asImageBitmap(),
            overlay = buildOverlay(mask).asImageBitmap(),
            coveragePercent = (mask.coverage() * 100).roundToInt()
        )
    } finally {
        segmenter.close()
    }
}

/** תמונה שקופה עם צבע אדום חצי-שקוף על הפיקסלים שזוהו כאנשים */
private fun buildOverlay(mask: PersonMask): Bitmap {
    val pixels = IntArray(mask.width * mask.height)
    val red = AndroidColor.argb(150, 255, 0, 0)
    for (i in pixels.indices) {
        if (mask.data[i]) pixels[i] = red
    }
    return Bitmap.createBitmap(pixels, mask.width, mask.height, Bitmap.Config.ARGB_8888)
}
