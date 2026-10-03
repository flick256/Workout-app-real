package app.forge.fitness.feature.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.fitness.ui.components.ConfirmDialog
import app.forge.fitness.ui.components.SectionHeader
import app.forge.fitness.ui.components.rememberHaptics
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Create a food from its label, or correct one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEditScreen(
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    vm: FoodEditViewModel = hiltViewModel(),
) {
    val form by vm.form.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() = scope.launch {
        val id = vm.save()
        if (id != null) { haptics.success(); onSaved(id) } else haptics.reject()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (form.isNew) "New food" else "Edit food") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Cancel") } },
                actions = {
                    if (!form.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete food") }
                    Button(onClick = { save() }, enabled = !form.saving, modifier = Modifier.padding(end = Spacing.sm)) { Text("Save") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (!form.loaded) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> vm.update { it.copy(name = v) } },
                label = { Text("Name") },
                placeholder = { Text("e.g. Chicken wrap") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = form.brand,
                    onValueChange = { v -> vm.update { it.copy(brand = v) } },
                    label = { Text("Brand (optional)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.barcode,
                    onValueChange = { v -> vm.update { it.copy(barcode = v.filter(Char::isDigit).take(14)) } },
                    label = { Text("Barcode") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            SectionHeader("Serving")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                NumberField("Serving size", "g", form.servingG, Modifier.weight(1f)) { v -> vm.update { it.copy(servingG = v) } }
                OutlinedTextField(
                    value = form.servingLabel,
                    onValueChange = { v -> vm.update { it.copy(servingLabel = v) } },
                    label = { Text("Called (optional)") },
                    placeholder = { Text("1 bar") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            SectionHeader("Nutrition information")
            LabelScanButtons(reading = form.reading, onImage = vm::scanLabel)
            form.scanNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            val options = listOf(false to "Per 100 g", true to "Per serving")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, (perServing, label) ->
                    SegmentedButton(
                        selected = form.perServing == perServing,
                        onClick = { vm.update { it.copy(perServing = perServing) } },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) { Text(label) }
                }
            }
            NumberField("Energy", "kcal", form.kcal, Modifier.fillMaxWidth()) { v -> vm.update { it.copy(kcal = v) } }
            form.energyWarning?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                NumberField("Protein", "g", form.protein, Modifier.weight(1f)) { v -> vm.update { it.copy(protein = v) } }
                NumberField("Carbs", "g", form.carbs, Modifier.weight(1f)) { v -> vm.update { it.copy(carbs = v) } }
                NumberField("Fat", "g", form.fat, Modifier.weight(1f)) { v -> vm.update { it.copy(fat = v) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                NumberField("Fibre", "g", form.fiber, Modifier.weight(1f)) { v -> vm.update { it.copy(fiber = v) } }
                NumberField("Sugars", "g", form.sugar, Modifier.weight(1f)) { v -> vm.update { it.copy(sugar = v) } }
                NumberField("Salt", "g", form.salt, Modifier.weight(1f)) { v -> vm.update { it.copy(salt = v) } }
            }
            Text(
                "Australian labels list sodium in mg: salt (g) = sodium (mg) × 2.5 ÷ 1000.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            form.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(Spacing.md))
            Button(
                onClick = { save() },
                enabled = !form.saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.bigTouch),
            ) { Text("Save food") }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this food?",
            message = "It disappears from search and recents. Anything you've already logged stays in your diary.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                scope.launch { vm.delete(); onClose() }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Photograph the nutrition panel (or pick a photo); Forge reads it on the phone. */
@Composable
private fun LabelScanButtons(reading: Boolean, onImage: (android.net.Uri) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Saveable: the camera app may push Forge out of memory while it's open.
    var pending by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    val camera = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
    ) { ok -> if (ok) pending?.let(onImage) }
    val gallery = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onImage) }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.material3.FilledTonalButton(
            onClick = {
                val dir = java.io.File(context.cacheDir, "camera").apply { mkdirs() }
                val file = java.io.File(dir, "label-${System.currentTimeMillis()}.jpg")
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                pending = uri
                camera.launch(uri)
            },
            enabled = !reading,
            modifier = Modifier.weight(1f).heightIn(min = Sizes.touch),
        ) { Text("Scan label") }
        androidx.compose.material3.OutlinedButton(
            onClick = {
                gallery.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            },
            enabled = !reading,
            modifier = Modifier.weight(1f).heightIn(min = Sizes.touch),
        ) { Text("From photo") }
        if (reading) androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}
