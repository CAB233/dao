package win.zuoye.dao.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import win.zuoye.dao.data.ShiftTemplate
import win.zuoye.dao.domain.formatShiftEnd

/**
 * 跨零点标记的取法。
 *
 * 踩过：从带占位符的 `shift_next_day` 里「抠」标记时，`trimEnd()` 会把英文 `Next day ` 必需的尾随空格一起 吃掉，界面上出现「Next
 * day07:30」。所以改成独立资源 `shift_next_day_marker`，这里把中英两种取值都钉住。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NextDayMarkerTest {

    private val night =
        ShiftTemplate(
            id = 1L,
            name = "夜班",
            startMinute = 22 * 60 + 30,
            endMinute = 7 * 60 + 30,
            colorArgb = 0,
        )

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    @Test
    @Config(qualifiers = "zh")
    fun `chinese marker has no trailing space and prefixes the time`() {
        val marker = nextDayMarker(context())

        assertEquals("次日", marker)
        assertEquals("次日07:30", formatShiftEnd(night, marker))
    }

    @Test
    @Config(qualifiers = "en")
    fun `english marker keeps the space that separates it from the time`() {
        val marker = nextDayMarker(context())

        // 关键：空格必须留着，否则会拼成 "Next day07:30"
        assertTrue(
            marker.endsWith(" "),
            "english marker must keep its trailing space, was: ${marker.replace(" ", "·")}",
        )
        assertEquals("Next day 07:30", formatShiftEnd(night, marker))
    }
}
