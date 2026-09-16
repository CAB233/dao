package win.zuoye.dao.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {
    @Test
    fun comparesNumericVersionComponents() {
        assertTrue(isNewerVersion("0.0.6", "0.0.5"))
        assertTrue(isNewerVersion("v1.10.0", "1.9.9"))
        assertTrue(isNewerVersion("2.0", "1.99.99"))
        assertFalse(isNewerVersion("1.0.0", "1.0"))
        assertFalse(isNewerVersion("0.0.4", "0.0.5"))
    }

    @Test
    fun ignoresPreReleaseAndBuildSuffixesForNumericComparison() {
        assertTrue(isNewerVersion("1.1.0-beta01", "1.0.9"))
        assertFalse(isNewerVersion("1.0.0+5", "1.0.0"))
    }

    @Test
    fun invalidVersionsAreNotConsideredNewer() {
        assertFalse(isNewerVersion("latest", "1.0.0"))
        assertFalse(isNewerVersion("1.0.0", "debug"))
        assertFalse(isNewerVersion("1.x.0", "1.0.0"))
    }
}
