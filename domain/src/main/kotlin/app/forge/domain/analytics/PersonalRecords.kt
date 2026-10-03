package app.forge.domain.analytics

import app.forge.domain.calc.OneRepMax

/**
 * One completed work set of one exercise, for record keeping. [loadKg] is what you
 * actually moved (bodyweight share included for calisthenics).
 */
data class SetPoint(
    val sessionId: String,
    val atMillis: Long,
    val loadKg: Double?,
    val reps: Int?,
    val rpe: Double? = null,
    val seconds: Int? = null,
)

enum class RecordKind(val label: String) {
    E1RM("Best estimated 1RM"),
    HEAVIEST("Heaviest load"),
    MOST_REPS("Most reps in a set"),
    SESSION_VOLUME("Most volume in a workout"),
    LONGEST_HOLD("Longest hold"),
}

data class Record(val kind: RecordKind, val value: Double, val sessionId: String, val atMillis: Long, val reps: Int? = null)

/**
 * Your bests for one exercise. A record is "new" in a workout only if it beats
 * everything from *earlier* workouts, so the first time you do an exercise doesn't
 * flood you with PRs.
 */
object PersonalRecords {

    fun best(sets: List<SetPoint>): Map<RecordKind, Record> {
        val out = mutableMapOf<RecordKind, Record>()
        fun offer(r: Record) {
            val current = out[r.kind]
            if (current == null || r.value > current.value + 1e-9) out[r.kind] = r
        }
        sets.forEach { s ->
            val load = s.loadKg?.takeIf { it > 0 }
            val reps = s.reps?.takeIf { it > 0 }
            if (load != null && reps != null) {
                OneRepMax.estimate(load, reps, s.rpe)?.let { offer(Record(RecordKind.E1RM, it, s.sessionId, s.atMillis, reps)) }
            }
            if (load != null) offer(Record(RecordKind.HEAVIEST, load, s.sessionId, s.atMillis, reps))
            if (reps != null) offer(Record(RecordKind.MOST_REPS, reps.toDouble(), s.sessionId, s.atMillis, reps))
            s.seconds?.takeIf { it > 0 }?.let { offer(Record(RecordKind.LONGEST_HOLD, it.toDouble(), s.sessionId, s.atMillis)) }
        }
        sets.filter { it.loadKg != null && it.reps != null }.groupBy { it.sessionId }.forEach { (id, s) ->
            val volume = s.sumOf { it.loadKg!! * it.reps!! }
            if (volume > 0) offer(Record(RecordKind.SESSION_VOLUME, volume, id, s.first().atMillis))
        }
        return out
    }

    /** Records set in [sessionId] that beat every earlier workout (needs at least one earlier workout). */
    fun newInSession(sets: List<SetPoint>, sessionId: String): List<Record> {
        val session = sets.filter { it.sessionId == sessionId }
        if (session.isEmpty()) return emptyList()
        val start = session.minOf { it.atMillis }
        val before = sets.filter { it.sessionId != sessionId && it.atMillis < start }
        if (before.isEmpty()) return emptyList()
        val old = best(before)
        return best(session).values.filter { r -> old[r.kind]?.let { r.value > it.value + 1e-9 } ?: false }
            .sortedBy { it.kind.ordinal }
    }

    /** Best e1RM per workout, oldest first: the strength chart's line. */
    fun e1rmSeries(sets: List<SetPoint>): List<Pair<Long, Double>> =
        sets.groupBy { it.sessionId }.values.mapNotNull { s ->
            s.mapNotNull { p -> p.loadKg?.takeIf { it > 0 }?.let { l -> p.reps?.let { OneRepMax.estimate(l, it, p.rpe) } } }
                .maxOrNull()?.let { s.minOf { p -> p.atMillis } to it }
        }.sortedBy { it.first }
}
