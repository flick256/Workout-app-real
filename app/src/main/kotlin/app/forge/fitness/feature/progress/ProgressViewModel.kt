package app.forge.fitness.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forge.domain.model.BodyMetricKind
import app.forge.domain.model.WeightUnit
import app.forge.fitness.data.analytics.AnalyticsRepository
import app.forge.fitness.data.analytics.DemoDataLoader
import app.forge.fitness.data.analytics.Overview
import app.forge.fitness.data.db.BodyMetricEntity
import app.forge.fitness.data.prefs.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProgressState(
    val overview: Overview = Overview(),
    val unit: WeightUnit = WeightUnit.KG,
    val latestWeight: BodyMetricEntity? = null,
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    analytics: AnalyticsRepository,
    preferences: UserPreferencesRepository,
    private val demo: DemoDataLoader,
) : ViewModel() {

    val state: StateFlow<ProgressState> = combine(
        analytics.observeOverview(),
        preferences.preferences,
        analytics.observeBodyMetrics(),
    ) { overview, prefs, body ->
        ProgressState(overview, prefs.weightUnit, body.firstOrNull { it.kind == BodyMetricKind.WEIGHT })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressState())

    suspend fun loadDemo(): Int = demo.load()
}
