package app.forge.fitness.di

import javax.inject.Qualifier

/** A coroutine scope that lives as long as the app process (for work that must finish). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Wall-clock time, injectable so tests can control it. */
fun interface TimeSource {
    fun now(): Long
}
