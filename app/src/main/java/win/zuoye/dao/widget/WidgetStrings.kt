package win.zuoye.dao.widget

import android.content.Context
import win.zuoye.dao.R

/**
 * 跨零点班次结束时刻的前缀标记，给 `formatShiftEnd` 直接拼在时间前面。
 *
 * **分隔空格在代码里补，不放进字符串资源**：aapt2 会把 `<string>` 的尾随空白裁掉，资源里写 `Next day ` 拿到的其实是 `Next day`。同理也不要从带
 * `%1$s` 的 `shift_next_day` 里"抠"标记—— 抠的过程很容易把空格一起 trim 掉，拼出「Next day07:30」（踩过两次）。
 */
fun nextDayMarker(context: Context): String {
    val text = context.getString(R.string.shift_next_day_marker)
    // 末尾是 ASCII 字母（英文）才补分隔空格；中文「次日07:30」紧排，不要空格
    // （不能用 Char.isLetter()：汉字也是 letter，会导致中文多一个空格）
    val endsWithLatin = text.lastOrNull()?.let { it in 'a'..'z' || it in 'A'..'Z' } == true
    return if (endsWithLatin) "$text " else text
}
