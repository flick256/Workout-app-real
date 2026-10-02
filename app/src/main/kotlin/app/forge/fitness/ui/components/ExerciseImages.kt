package app.forge.fitness.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * free-exercise-db images are bundled as WebP under assets/exercise_images. The dataset
 * lists them as "Pushups/0.jpg"; this maps that to the bundled file.
 */
fun exerciseImageUri(path: String): String =
    "file:///android_asset/exercise_images/" + path.substringBeforeLast('.') + ".webp"

/** The start and end positions, alternating like a slow animation. */
@Composable
fun ExerciseDemo(images: List<String>, contentDescription: String, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(3f / 2f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (images.isEmpty()) {
            Icon(
                Icons.Rounded.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dp(48f)),
            )
        } else {
            val index by produceState(0, images) {
                while (images.size > 1) {
                    delay(1_300)
                    value = (value + 1) % images.size
                }
            }
            Crossfade(targetState = index, animationSpec = tween(450), label = "exerciseDemo") { i ->
                AsyncImage(
                    model = exerciseImageUri(images[i]),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Small square thumbnail for lists. */
@Composable
fun ExerciseThumb(images: List<String>, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val first = images.firstOrNull()
        if (first == null) {
            Icon(
                Icons.Rounded.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2),
            )
        } else {
            AsyncImage(
                model = exerciseImageUri(first),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
