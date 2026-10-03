package app.forge.fitness.feature.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forge.domain.model.Equipment
import app.forge.domain.model.WeightUnit
import app.forge.domain.nutrition.ActivityLevel
import app.forge.domain.nutrition.NutritionGoal
import app.forge.domain.nutrition.Sex
import app.forge.fitness.ui.components.BigButton
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing

private const val STEPS = 5

/**
 * First-run setup: a few quick questions so suggestions, loads and food targets fit you
 * from day one. Everything can be skipped and changed later in Settings.
 */
@Composable
fun SetupScreen(
    onDone: (connectStrap: Boolean) -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val a by viewModel.answers.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    LaunchedEffect(done) { done?.let(onDone) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step-- }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touch)) {
                LinearProgressIndicator(
                    progress = { (step + 1f) / STEPS },
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.finish(skipped = true) }, enabled = !saving) { Text("Skip") }
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "setup step",
                modifier = Modifier.weight(1f),
            ) { s ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    when (s) {
                        0 -> Welcome(a, viewModel::edit)
                        1 -> EquipmentStep(a, viewModel::edit)
                        2 -> AboutYou(a, viewModel::edit)
                        3 -> GoalStep(a, viewModel::edit)
                        else -> Extras(a, viewModel::edit)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(vertical = Spacing.md)) {
                if (step > 0) {
                    OutlinedButton(onClick = { step-- }, modifier = Modifier.heightIn(min = Sizes.bigTouch)) { Text("Back") }
                }
                BigButton(
                    text = if (step < STEPS - 1) "Next" else "Start using Forge",
                    onClick = { if (step < STEPS - 1) step++ else viewModel.finish(skipped = false) },
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Title(text: String, body: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Welcome(a: SetupAnswers, edit: ((SetupAnswers) -> SetupAnswers) -> Unit) {
    Title(
        "Welcome to Forge",
        "Your training, food and habits in one place. Everything stays on this phone: no account, no ads, " +
            "works offline. A few quick questions and you're in.",
    )
    Spacer(Modifier.heightIn(min = Spacing.md))
    Text("Weights in", style = MaterialTheme.typography.titleSmall)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        WeightUnit.entries.forEachIndexed { i, unit ->
            SegmentedButton(
                selected = a.unit == unit,
                onClick = { edit { it.withUnit(unit) } },
                shape = SegmentedButtonDefaults.itemShape(i, WeightUnit.entries.size),
            ) { Text(if (unit == WeightUnit.KG) "Kilograms (kg)" else "Pounds (lb)") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EquipmentStep(a: SetupAnswers, edit: ((SetupAnswers) -> SetupAnswers) -> Unit) {
    Title(
        "What do you train with?",
        "Exercises, programs and swaps are picked from what you have. You can add the exact weights you own " +
            "later in Settings, so suggestions land on real plates and dumbbells.",
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Equipment.entries.filter { it != Equipment.OTHER }.forEach { item ->
            val on = item in a.equipment
            FilterChip(
                selected = on,
                onClick = { edit { it.copy(equipment = if (on) it.equipment - item else it.equipment + item) } },
                label = { Text(item.label) },
                modifier = Modifier.heightIn(min = Sizes.touch),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutYou(a: SetupAnswers, edit: ((SetupAnswers) -> SetupAnswers) -> Unit) {
    Title(
        "About you",
        "Used for bodyweight-exercise loads, food targets and heart-rate zones. All optional, and it never leaves your phone.",
    )
    NumberField("Bodyweight", a.bodyweight, a.unit.symbol, KeyboardType.Decimal) { v -> edit { it.copy(bodyweight = v) } }
    NumberField("Height", a.heightCm, "cm", KeyboardType.Decimal) { v -> edit { it.copy(heightCm = v) } }
    NumberField("Year of birth", a.birthYear, null, KeyboardType.Number, maxLength = 4) { v -> edit { it.copy(birthYear = v) } }
    Text("Sex (for the calorie formula)", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Sex.entries.forEach { sex ->
            FilterChip(
                selected = a.sex == sex,
                onClick = { edit { it.copy(sex = if (it.sex == sex) null else sex) } },
                label = { Text(sex.label) },
                modifier = Modifier.heightIn(min = Sizes.touch),
            )
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    suffix: String?,
    type: KeyboardType,
    maxLength: Int = 6,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' || it == ',' }.take(maxLength)) },
        label = { Text(label) },
        suffix = suffix?.let { s -> @Composable { Text(s) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalStep(a: SetupAnswers, edit: ((SetupAnswers) -> SetupAnswers) -> Unit) {
    Title(
        "Your goal",
        "Sets your food targets. Forge keeps them safe for a growing body: fat loss is gentle and protein stays high.",
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        NutritionGoal.entries.forEach { goal ->
            FilterChip(
                selected = a.goal == goal,
                onClick = { edit { it.copy(goal = goal) } },
                label = { Text(goal.label) },
                modifier = Modifier.heightIn(min = Sizes.touch),
            )
        }
    }
    Text("How active are you?", style = MaterialTheme.typography.titleSmall)
    ActivityLevel.entries.forEach { level ->
        FilterChip(
            selected = a.activity == level,
            onClick = { edit { it.copy(activity = level) } },
            label = {
                Column(Modifier.padding(vertical = Spacing.xs)) {
                    Text(level.label)
                    Text(level.description, style = MaterialTheme.typography.bodySmall)
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touch),
        )
    }
}

@Composable
private fun Extras(a: SetupAnswers, edit: ((SetupAnswers) -> SetupAnswers) -> Unit) {
    Title("Almost done", "Two optional extras. Both can be changed any time in Settings.")
    SwitchRow(
        title = "Live heart rate from a strap",
        body = "Records heart rate during Forge workouts from a Bluetooth strap (like the Amazfit Helio Strap with " +
            "Heart Rate Push on). You'll pick it next.",
        checked = a.connectStrap,
    ) { on -> edit { it.copy(connectStrap = on) } }
    SwitchRow(
        title = "Explore with demo data",
        body = "Adds 12 weeks of example workouts so you can see the charts. Remove it in Settings with one tap; " +
            "your own data is never touched.",
        checked = a.demoData,
    ) { on -> edit { it.copy(demoData = on) } }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touch)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = Spacing.sm),
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.md)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
