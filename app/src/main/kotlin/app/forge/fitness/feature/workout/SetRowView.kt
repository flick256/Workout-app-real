package app.forge.fitness.feature.workout

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.forge.domain.calc.Units
import app.forge.domain.model.LogType
import app.forge.domain.model.SetType
import app.forge.domain.model.WeightUnit
import app.forge.fitness.data.db.SetEntryEntity
import app.forge.fitness.ui.components.rememberHaptics
import app.forge.fitness.ui.format.Format
import app.forge.fitness.ui.theme.tabular

/** Column widths shared by the header and every row, so everything lines up. */
internal object SetColumns {
    val set = 36.dp
    val check = 48.dp
    val rpe = 44.dp
}

@Composable
internal fun SetHeader(logType: LogType, unit: WeightUnit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderText("SET", Modifier.width(SetColumns.set))
        HeaderText("PREVIOUS", Modifier.weight(1.3f))
        when (logType) {
            LogType.WEIGHT_REPS -> {
                HeaderText(unit.symbol.uppercase(), Modifier.weight(1f))
                HeaderText("REPS", Modifier.weight(1f))
            }
            LogType.REPS -> {
                HeaderText("+${unit.symbol.uppercase()}", Modifier.weight(1f))
                HeaderText("REPS", Modifier.weight(1f))
            }
            LogType.DURATION -> HeaderText("TIME", Modifier.weight(2f))
            LogType.DISTANCE_DURATION -> {
                HeaderText("KM", Modifier.weight(1f))
                HeaderText("TIME", Modifier.weight(1f))
            }
        }
        HeaderText("RPE", Modifier.width(SetColumns.rpe))
        Box(Modifier.width(SetColumns.check))
    }
}

@Composable
private fun HeaderText(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/**
 * One set: [label] [previous] [inputs…] [RPE] [✓].
 *
 * Inputs keep their own text while you type and save every change straight to the
 * database. Empty inputs show last time's numbers as grey hints; ticking ✓ with
 * them empty uses those numbers.
 */
@Composable
internal fun SetRowView(
    row: SetRow,
    logType: LogType,
    unit: WeightUnit,
    onWeight: (Double?) -> Unit,
    onReps: (Int?) -> Unit,
    onDuration: (Int?) -> Unit,
    onDistance: (Double?) -> Unit,
    onRpe: (Double?) -> Unit,
    onType: (SetType) -> Unit,
    onDelete: () -> Unit,
    onToggleDone: () -> Unit,
) {
    val set = row.set
    val prev = row.previous
    val done = set.completedAt != null
    val haptics = rememberHaptics()
    val background by animateColorAsState(
        if (done) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent,
        label = "setBackground",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetTypeCell(row.label, set.type, onType, onDelete)
        Text(
            previousText(prev, logType, unit),
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.3f),
        )
        when (logType) {
            LogType.WEIGHT_REPS, LogType.REPS -> {
                WeightField(set, prev, unit, done, optional = logType == LogType.REPS, onWeight = onWeight)
                IntField(set.reps, prev?.reps, done, onReps)
            }
            LogType.DURATION -> DurationField(set.durationSeconds, prev?.durationSeconds, done, onDuration, Modifier.weight(2f))
            LogType.DISTANCE_DURATION -> {
                DistanceField(set.distanceMeters, prev?.distanceMeters, done, onDistance)
                DurationField(set.durationSeconds, prev?.durationSeconds, done, onDuration, Modifier.weight(1f))
            }
        }
        RpeCell(set.rpe, onRpe)
        DoneButton(done) {
            if (done) haptics.tick() else haptics.success()
            onToggleDone()
        }
    }
}

private fun previousText(prev: SetEntryEntity?, logType: LogType, unit: WeightUnit): String {
    if (prev == null) return "–"
    return when (logType) {
        LogType.WEIGHT_REPS -> "${prev.weightKg?.let { Format.weightNumber(it, unit) } ?: "–"} × ${prev.reps ?: "–"}"
        LogType.REPS -> {
            val added = prev.weightKg?.takeIf { it > 0 }?.let { "+${Format.weightNumber(it, unit)} " }.orEmpty()
            "$added${prev.reps ?: "–"} reps"
        }
        LogType.DURATION -> prev.durationSeconds?.let { Format.duration(it.toLong()) } ?: "–"
        LogType.DISTANCE_DURATION -> buildString {
            prev.distanceMeters?.let { append(Units.format(it / 1000.0)).append(" km ") }
            prev.durationSeconds?.let { append(Format.duration(it.toLong())) }
        }.ifBlank { "–" }
    }
}

@Composable
private fun SetTypeCell(label: String, type: SetType, onType: (SetType) -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val color = when (type) {
        SetType.WARMUP -> MaterialTheme.colorScheme.tertiary
        SetType.DROP, SetType.FAILURE -> MaterialTheme.colorScheme.secondary
        SetType.WORKING -> MaterialTheme.colorScheme.onSurface
    }
    Box(Modifier.width(SetColumns.set)) {
        Surface(
            onClick = { open = true },
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .size(SetColumns.set, 40.dp)
                .semantics { contentDescription = "Set $label, change type" },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelLarge.tabular(), color = color)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                SetType.WORKING to "Normal set",
                SetType.WARMUP to "Warm-up",
                SetType.DROP to "Drop set",
                SetType.FAILURE to "To failure",
            ).forEach { (t, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    leadingIcon = if (t == type) ({ Icon(Icons.Rounded.Check, null) }) else null,
                    onClick = { open = false; onType(t) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Delete set", color = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

@Composable
private fun RowScope.WeightField(
    set: SetEntryEntity,
    prev: SetEntryEntity?,
    unit: WeightUnit,
    done: Boolean,
    optional: Boolean,
    onWeight: (Double?) -> Unit,
) {
    val saved = set.weightKg
    var text by remember(set.id, unit) { mutableStateOf(saved?.let { Format.weightNumber(it, unit) }.orEmpty()) }
    // Pick up changes made elsewhere (quick-pick chips, warm-ups, ticking with last time's numbers).
    LaunchedEffect(saved, unit) {
        if (Format.parseWeight(text, unit)?.let { Units.roundTo(it, 0.001) } != saved?.let { Units.roundTo(it, 0.001) }) {
            text = saved?.let { Format.weightNumber(it, unit) }.orEmpty()
        }
    }
    NumberCell(
        value = text,
        // For bodyweight moves this is only extra weight (vest, bag): leave it empty if none.
        hint = prev?.weightKg?.takeIf { it > 0 }?.let { Format.weightNumber(it, unit) } ?: if (optional) "–" else "0",
        keyboardType = KeyboardType.Decimal,
        done = done,
        modifier = Modifier.weight(1f),
        description = if (optional) "Added weight in ${unit.symbol}, optional" else "Weight in ${unit.symbol}",
    ) { input ->
        val cleaned = input.filter { it.isDigit() || it == '.' || it == ',' }.take(7)
        text = cleaned
        if (cleaned.isEmpty()) onWeight(null) else Format.parseWeight(cleaned, unit)?.let(onWeight)
    }
}

@Composable
private fun RowScope.IntField(value: Int?, hint: Int?, done: Boolean, onChange: (Int?) -> Unit) {
    var text by remember { mutableStateOf(value?.toString().orEmpty()) }
    LaunchedEffect(value) {
        if (text.toIntOrNull() != value) text = value?.toString().orEmpty()
    }
    NumberCell(
        value = text,
        hint = hint?.toString() ?: "0",
        keyboardType = KeyboardType.Number,
        done = done,
        modifier = Modifier.weight(1f),
        description = "Reps",
    ) { input ->
        val cleaned = input.filter(Char::isDigit).take(4)
        text = cleaned
        onChange(cleaned.toIntOrNull())
    }
}

@Composable
private fun DurationField(value: Int?, hint: Int?, done: Boolean, onChange: (Int?) -> Unit, modifier: Modifier) {
    var text by remember { mutableStateOf(value?.let { Format.duration(it.toLong()) }.orEmpty()) }
    LaunchedEffect(value) {
        if (Format.parseDuration(text) != value) text = value?.let { Format.duration(it.toLong()) }.orEmpty()
    }
    NumberCell(
        value = text,
        hint = hint?.let { Format.duration(it.toLong()) } ?: "0:00",
        keyboardType = KeyboardType.Number,
        done = done,
        modifier = modifier,
        description = "Time, minutes colon seconds",
    ) { input ->
        val cleaned = input.filter { it.isDigit() || it == ':' }.take(7)
        text = cleaned
        if (cleaned.isEmpty()) onChange(null) else Format.parseDuration(cleaned)?.let(onChange)
    }
}

@Composable
private fun RowScope.DistanceField(meters: Double?, hint: Double?, done: Boolean, onChange: (Double?) -> Unit) {
    var text by remember { mutableStateOf(meters?.let { Units.format(it / 1000.0) }.orEmpty()) }
    LaunchedEffect(meters) {
        val typed = text.replace(',', '.').toDoubleOrNull()?.times(1000)
        if (typed != meters) text = meters?.let { Units.format(it / 1000.0) }.orEmpty()
    }
    NumberCell(
        value = text,
        hint = hint?.let { Units.format(it / 1000.0) } ?: "0",
        keyboardType = KeyboardType.Decimal,
        done = done,
        modifier = Modifier.weight(1f),
        description = "Distance in kilometres",
    ) { input ->
        val cleaned = input.filter { it.isDigit() || it == '.' || it == ',' }.take(6)
        text = cleaned
        if (cleaned.isEmpty()) onChange(null) else cleaned.replace(',', '.').toDoubleOrNull()?.let { onChange(it * 1000) }
    }
}

/** A compact, centred number input: big enough to hit, small enough to fit 4 per row. */
@Composable
private fun NumberCell(
    value: String,
    hint: String,
    keyboardType: KeyboardType,
    done: Boolean,
    modifier: Modifier,
    description: String,
    onValueChange: (String) -> Unit,
) {
    val style = MaterialTheme.typography.titleMedium.tabular().copy(
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        modifier = modifier
            .height(40.dp)
            .semantics { contentDescription = description },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(
                        if (done) androidx.compose.ui.graphics.Color.Transparent
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (value.isEmpty()) {
                    Text(hint, style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)))
                }
                inner()
            }
        },
    )
}

@Composable
private fun RpeCell(rpe: Double?, onRpe: (Double?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.width(SetColumns.rpe)) {
        Box(
            Modifier
                .size(SetColumns.rpe, 40.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable { open = true }
                .semantics { contentDescription = "RPE ${rpe?.let(Format::rpe) ?: "not set"}" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                rpe?.let(Format::rpe) ?: "–",
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = if (rpe != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("RPE: how hard was it?", style = MaterialTheme.typography.labelMedium) }, onClick = {}, enabled = false)
            RPE_OPTIONS.forEach { (value, meaning) ->
                DropdownMenuItem(
                    text = { Text("${Format.rpe(value)}  ·  $meaning") },
                    leadingIcon = if (value == rpe) ({ Icon(Icons.Rounded.Check, null) }) else null,
                    onClick = { open = false; onRpe(value) },
                )
            }
            DropdownMenuItem(text = { Text("Clear") }, onClick = { open = false; onRpe(null) })
        }
    }
}

private val RPE_OPTIONS = listOf(
    10.0 to "max effort",
    9.5 to "maybe 1 more",
    9.0 to "1 rep left",
    8.5 to "1–2 left",
    8.0 to "2 reps left",
    7.5 to "2–3 left",
    7.0 to "3 reps left",
    6.0 to "4+ left, easy",
)

@Composable
private fun DoneButton(done: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "doneColor",
    )
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = container,
        modifier = Modifier
            .size(SetColumns.check, 40.dp)
            .semantics { contentDescription = if (done) "Set done, tap to undo" else "Mark set done" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
