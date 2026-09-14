package win.zuoye.dao.ui.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import com.journeyapps.barcodescanner.ViewfinderView
import win.zuoye.dao.R

/**
 * 取景框：父类 [ViewfinderView] 挖的是**直角**洞，再叠一圈圆角描边的话，
 * 四个角上描边和洞口对不上、会留出缝隙；所以这里不再用父类的遮罩，
 * 改为自己画：压暗遮罩和白色描边用**同一条圆角路径**，两者严丝合缝。
 *
 * 取景区域仍是父类算好的 `framingRect`（由 `app:zxing_framing_rect_*` 定成正方形），
 * 所以框和真正拿去解码的区域也始终一致。
 */
class ScanFrameView(
    context: Context,
    attrs: AttributeSet?,
) : ViewfinderView(context, attrs) {

    private val density = resources.displayMetrics.density
    private val cornerRadius = CORNER_DP * density

    private val maskPaint = Paint().apply {
        color = context.getColor(R.color.scan_viewfinder_mask)
    }

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = FRAME_STROKE_DP * density
        color = context.getColor(R.color.scan_viewfinder_frame)
    }

    private val maskPath = Path()
    private val frameRect = RectF()
    private val holeRect = RectF()

    override fun onDraw(canvas: Canvas) {
        // 不走 super.onDraw：父类会再画一次直角洞遮罩和激光线，正是要避开的东西
        refreshSizes()
        val rect = framingRect ?: return
        if (rect.width() <= 0 || rect.height() <= 0) return

        holeRect.set(rect)
        maskPath.rewind()
        maskPath.fillType = Path.FillType.EVEN_ODD
        maskPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        maskPath.addRoundRect(holeRect, cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.drawPath(maskPath, maskPaint)

        // 描边压在同一条圆角路径上，线宽内外各一半，视觉上正好落在洞口边缘
        frameRect.set(rect)
        canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, framePaint)
    }

    private companion object {
        const val FRAME_STROKE_DP = 3f
        const val CORNER_DP = 20f
    }
}
