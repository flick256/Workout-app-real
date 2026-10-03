package app.forge.fitness.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emits now and then every [periodMs]. Combined into flows whose result depends on the
 * clock (recovery %, readiness), so they stay current while a screen is open, not only
 * when the database changes.
 */
fun ticker(periodMs: Long = 60_000L): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(periodMs)
    }
}
