package app.forge.domain.backup

/** What restoring one table from a backup will do. */
data class MergePlan<T>(
    /** In the backup, not on the phone. */
    val toInsert: List<T>,
    /** On both, and the backup's copy was edited more recently. */
    val toUpdate: List<T>,
    /** On both, and the phone's copy is the same or newer: left alone. */
    val kept: Int,
) {
    val changes: Int get() = toInsert.size + toUpdate.size
}

/**
 * Restoring merges record by record instead of wiping the phone: every record has a
 * UUID and an "edited at" time, and the newer copy wins. Deletes are soft (a timestamp),
 * so a delete is just another edit and travels the same way. Nothing on the phone is
 * ever removed by a restore.
 */
object BackupMerge {
    fun <T> plan(local: List<T>, incoming: List<T>, id: (T) -> String, updatedAt: (T) -> Long): MergePlan<T> {
        val byId = local.associateBy(id)
        val insert = mutableListOf<T>()
        val update = mutableListOf<T>()
        var kept = 0
        // If a backup somehow holds the same id twice, the newest copy is the one that counts.
        incoming.groupBy(id).values.map { copies -> copies.maxBy(updatedAt) }.forEach { theirs ->
            val mine = byId[id(theirs)]
            when {
                mine == null -> insert += theirs
                updatedAt(theirs) > updatedAt(mine) -> update += theirs
                else -> kept++
            }
        }
        return MergePlan(insert, update, kept)
    }
}
