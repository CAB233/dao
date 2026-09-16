package win.zuoye.dao.data

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanRepositoryTest {
    @Test
    fun corruptPlanIsCopiedToTimestampedBackup() {
        val directory = createTempDirectory("dao-plan-test").toFile()
        try {
            val plan = directory.resolve("plan.json").apply { writeText("broken") }

            val backup = backupCorruptPlan(plan, now = 123)

            requireNotNull(backup)
            assertEquals("plan.corrupt-123.json", backup.name)
            assertEquals("broken", backup.readText())
            assertTrue(plan.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun missingPlanDoesNotCreateBackup() {
        val directory = Files.createTempDirectory("dao-plan-test").toFile()
        try {
            assertNull(backupCorruptPlan(directory.resolve("plan.json"), now = 123))
            assertFalse(directory.resolve("plan.corrupt-123.json").exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
