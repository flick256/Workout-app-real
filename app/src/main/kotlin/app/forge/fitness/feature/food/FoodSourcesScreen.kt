package app.forge.fitness.feature.food

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.forge.fitness.data.ai.AiAssistant
import app.forge.fitness.data.nutrition.FatSecretClient
import app.forge.fitness.data.prefs.UserPreferences
import app.forge.fitness.data.prefs.UserPreferencesRepository
import app.forge.fitness.ui.components.ForgeCard
import app.forge.fitness.ui.components.SectionHeader
import app.forge.fitness.ui.theme.Sizes
import app.forge.fitness.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FoodSourcesViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val fatSecret: FatSecretClient,
    ai: AiAssistant,
) : ViewModel() {
    val prefs: StateFlow<UserPreferences?> = preferences.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val aiInstalled = ai.isAvailable
    val testing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun saveFatSecret(id: String, secret: String) {
        if (testing.value) return
        testing.value = true
        message.value = null
        viewModelScope.launch {
            val problem = fatSecret.test(id, secret)
            if (problem == null) {
                preferences.setFatSecret(id, secret)
                message.value = "Connected. Brand and chain foods now show when you search."
            } else {
                message.value = problem
            }
            testing.value = false
        }
    }

    fun removeFatSecret() {
        viewModelScope.launch { preferences.setFatSecret(null, null) }
        message.value = "FatSecret removed."
    }

    fun setWebLookup(on: Boolean) {
        viewModelScope.launch { preferences.setWebFoodLookup(on) }
    }
}

/** Where food search looks: the built-in database, Open Food Facts, FatSecret, the web. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSourcesScreen(onBack: () -> Unit, onOpenAi: () -> Unit, vm: FoodSourcesViewModel = hiltViewModel()) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val testing by vm.testing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var id by rememberSaveable { mutableStateOf("") }
    var secret by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(prefs?.fatSecretClientId) { prefs?.fatSecretClientId?.let { if (id.isEmpty()) id = it } }
    val connected = prefs?.fatSecretClientId != null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Food sources") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screen, end = Spacing.screen,
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Text(
                    "When you search, Forge looks in this order: foods on your phone, 3,700+ everyday Australian foods " +
                        "(built in, offline), then brands and chains online. Anything you pick is saved, so it's instant next time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { SectionHeader("FatSecret (brands & chains)") }
            item {
                ForgeCard {
                    Text(
                        if (connected) "Connected. McDonald's, KFC and 2 million other foods show up in search."
                        else "Free, about 2 minutes to set up once. Adds McDonald's, KFC, Subway and 2 million other " +
                            "foods. Its free database is the US one, so some Australian items differ; the web lookup below covers those.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "1. Make a free account at platform.fatsecret.com (Register).\n" +
                            "2. In your account, open API Keys and copy the Client ID and Client Secret (OAuth 2.0).\n" +
                            "3. Open IP Restrictions and add 0.0.0.0/0, so it works on mobile data and any Wi-Fi.\n" +
                            "4. Paste both below and tap Connect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.fatsecret.com/register"))) }
                    }) { Text("Open platform.fatsecret.com") }
                    OutlinedTextField(
                        value = id,
                        onValueChange = { id = it.trim() },
                        label = { Text("Client ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = secret,
                        onValueChange = { secret = it.trim() },
                        label = { Text(if (connected) "Client Secret (saved; paste again to change)" else "Client Secret") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                    )
                    message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.sm)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                        Button(
                            onClick = { vm.saveFatSecret(id, secret) },
                            enabled = !testing && id.isNotBlank() && secret.isNotBlank(),
                            modifier = Modifier.heightIn(min = Sizes.touch),
                        ) { Text(if (testing) "Checking…" else "Connect") }
                        if (connected) {
                            OutlinedButton(onClick = vm::removeFatSecret, modifier = Modifier.heightIn(min = Sizes.touch)) { Text("Remove") }
                        }
                    }
                    Text(
                        "Your key stays on this phone and isn't included in backups. Food data powered by fatsecret.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
            item { SectionHeader("Look it up on the web") }
            item {
                ForgeCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Sizes.touch)
                            .toggleable(value = prefs?.webFoodLookup ?: true, role = Role.Switch, onValueChange = vm::setWebLookup),
                    ) {
                        Column(Modifier.weight(1f).padding(end = Spacing.md)) {
                            Text("Web lookup", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "For anything the databases don't have (new menu items, local shops): Forge searches the web, " +
                                    "reads the nutrition page and shows you the numbers and where they came from before saving.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = prefs?.webFoodLookup ?: true, onCheckedChange = null)
                    }
                    Text(
                        if (vm.aiInstalled) "The on-device AI reads pages, so most layouts work."
                        else "Without the on-device AI, Forge can only read pages with a plain nutrition table. Download the AI " +
                            "model for better results.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                    if (!vm.aiInstalled) TextButton(onClick = onOpenAi) { Text("On-device AI settings") }
                }
            }
        }
    }
}
