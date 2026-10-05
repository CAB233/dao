package win.zuoye.dao.ui.scan

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import win.zuoye.dao.R

/** 屏幕与图像分析共用的正方形取景尺寸。 */
internal fun scanFrameSize(width: Float, height: Float, density: Float): Float =
    minOf(240f * density, minOf(width, height) * 0.8f)

@Composable
internal fun ScanFrame(modifier: Modifier = Modifier) {
    val maskColor = colorResource(R.color.scan_viewfinder_mask)
    val frameColor = colorResource(R.color.scan_viewfinder_frame)
    Box(
        modifier.drawWithCache {
            val side = scanFrameSize(size.width, size.height, density)
            val offset = Offset((size.width - side) / 2, (size.height - side) / 2)
            val radius = CornerRadius(20.dp.toPx())
            val mask =
                Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    addRoundRect(RoundRect(Rect(offset, Size(side, side)), radius))
                }
            val stroke = Stroke(3.dp.toPx())
            onDrawBehind {
                drawPath(mask, maskColor)
                drawRoundRect(frameColor, offset, Size(side, side), radius, style = stroke)
            }
        }
    )
}
