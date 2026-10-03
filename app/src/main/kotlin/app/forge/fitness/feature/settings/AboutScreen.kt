package app.forge.fitness.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.components.SectionHeader
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing

private data class Credit(val name: String, val use: String, val licence: String, val url: String)

private val DATA_CREDITS = listOf(
    Credit(
        "free-exercise-db",
        "Exercise library: names, instructions and images (by yuhonas and contributors)",
        "Unlicense (public domain)",
        "https://github.com/yuhonas/free-exercise-db",
    ),
    Credit(
        "Open Food Facts",
        "Food search and barcode lookups. Data © Open Food Facts contributors",
        "Open Database License (ODbL) 1.0; images CC BY-SA",
        "https://world.openfoodfacts.org",
    ),
    Credit(
        "Gemma 4 E2B (optional download)",
        "On-device AI for summaries, plateau explanations and quick logging",
        "Apache License 2.0",
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
    ),
)

private val LIBRARIES = listOf(
    Credit("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Language and core libraries", "Apache License 2.0", "https://kotlinlang.org"),
    Credit(
        "Android Jetpack",
        "Compose, Material 3, Room, Navigation, DataStore, WorkManager, Glance, Lifecycle, Core, Health Connect client",
        "Apache License 2.0",
        "https://developer.android.com/jetpack",
    ),
    Credit("Dagger Hilt", "Dependency injection", "Apache License 2.0", "https://dagger.dev/hilt"),
    Credit("LiteRT-LM", "Runs the on-device AI model", "Apache License 2.0", "https://github.com/google-ai-edge/LiteRT-LM"),
    Credit("Coil", "Image loading", "Apache License 2.0", "https://coil-kt.github.io/coil"),
    Credit("Reorderable", "Drag to reorder lists", "Apache License 2.0", "https://github.com/Calvin-LL/Reorderable"),
    Credit(
        "Google code scanner and ML Kit text recognition",
        "Barcode scanning and nutrition-label reading, on the phone",
        "Google APIs Terms of Service",
        "https://developers.google.com/ml-kit/terms",
    ),
)

/** Version, privacy in one paragraph, and the open data and code Forge is built on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("About Forge") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                ForgeCard {
                    Text("Forge $version", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "A personal training, food and habit tracker. Free, offline-first, no account, no ads, no " +
                            "analytics. Your data stays on this phone unless you back it up to your own Google Drive.\n\n" +
                            "The internet is only used to look up foods on Open Food Facts and, if you ask, to download " +
                            "the AI model. Heart rate comes straight from your strap over Bluetooth.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { SectionHeader("Data and models") }
            items(DATA_CREDITS, key = { it.name }) { CreditCard(it, ::open) }
            item { SectionHeader("Open-source libraries") }
            items(LIBRARIES, key = { it.name }) { CreditCard(it, ::open) }
            item {
                Text(
                    "Health advice in Forge is general information, not medical advice. Food targets follow standard " +
                        "formulas with limits suited to teenagers; check with a doctor or dietitian before big changes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun CreditCard(credit: Credit, open: (String) -> Unit) {
    ForgeCard {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.touch)
                .clickable(role = Role.Button, onClickLabel = "Open website") { open(credit.url) },
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(credit.name, style = MaterialTheme.typography.titleSmall)
            Text(credit.use, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(credit.licence, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
