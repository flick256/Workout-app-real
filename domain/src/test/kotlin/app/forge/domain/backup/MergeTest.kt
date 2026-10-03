package app.forge.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals

class MergeTest {
    private data class Row(val id: String, val value: String, val updatedAt: Long, val deletedAt: Long? = null)

    private fun plan(local: List<Row>, incoming: List<Row>) = BackupMerge.plan(local, incoming, { it.id }, { it.updatedAt })

    @Test
    fun `new records are added, newer ones update, older ones are kept`() {
        val local = listOf(Row("a", "mine", 10), Row("b", "mine", 50), Row("c", "same", 30))
        val incoming = listOf(Row("a", "theirs", 20), Row("b", "theirs", 40), Row("c", "same", 30), Row("d", "new", 5))
        val p = plan(local, incoming)
        assertEquals(listOf(Row("d", "new", 5)), p.toInsert)
        assertEquals(listOf(Row("a", "theirs", 20)), p.toUpdate)
        assertEquals(2, p.kept)
        assertEquals(2, p.changes)
    }

    @Test
    fun `a newer delete in the backup carries over`() {
        val p = plan(listOf(Row("a", "x", 10)), listOf(Row("a", "x", 15, deletedAt = 15)))
        assertEquals(15L, p.toUpdate.single().deletedAt)
    }

    @Test
    fun `duplicates in a backup resolve to the newest`() {
        val p = plan(emptyList(), listOf(Row("a", "old", 1), Row("a", "new", 2)))
        assertEquals("new", p.toInsert.single().value)
    }

    @Test
    fun `restoring the same backup twice changes nothing`() {
        val rows = listOf(Row("a", "x", 1), Row("b", "y", 2))
        val p = plan(rows, rows)
        assertEquals(0, p.changes)
        assertEquals(2, p.kept)
    }
}
