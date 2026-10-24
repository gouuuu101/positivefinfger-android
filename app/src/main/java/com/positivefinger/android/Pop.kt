package com.positivefinger.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

object Pop {
    val ink = Color.rgb(38, 33, 59)
    val paper = Color.rgb(255, 250, 235)
    val pink = Color.rgb(255, 110, 158)
    val yellow = Color.rgb(255, 214, 66)
    val purple = Color.rgb(115, 84, 217)
    val colors = listOf(pink, Color.rgb(122, 199, 255), yellow, Color.rgb(112, 212, 173), Color.rgb(191, 161, 255), Color.rgb(255, 171, 110))
    val names = listOf("もも", "ソーダ", "レモン", "ミント", "ライラック", "ピーチ")
    val notes = listOf("のんびり、きみのペースで。", "ちょっとひと息、しゅわっと。", "小さなできたを、ひとつずつ。", "いつものことばに、深呼吸。", "今日はどんなことばに会える？", "ありがとうって、あったかい。")
}
/** Native vector companion, matching the iOS capsule silhouette and six colors. */
class CompanionView(context: Context, private val index: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init { contentDescription = "相棒の" + Pop.names[index]; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = minOf(height.toFloat(), width * 1.05f) * .94f
        val x = width / 2f; val y = height / 2f
        fun oval(color: Int, left: Float, top: Float, right: Float, bottom: Float) {
            paint.color = color; paint.style = Paint.Style.FILL; canvas.drawOval(RectF(left, top, right, bottom), paint)
        }
        oval(0x2226213B, x-h*.375f, y+h*.385f, x+h*.375f, y+h*.475f)
        val body = RectF(x-h*.285f, y-h*.43f, x+h*.285f, y+h*.43f)
        paint.color = Pop.colors[index]; canvas.drawRoundRect(body,h*.285f,h*.285f,paint)
        paint.color = Pop.ink; paint.style = Paint.Style.STROKE; paint.strokeWidth = resources.displayMetrics.density * 2.5f
        canvas.drawRoundRect(body,h*.285f,h*.285f,paint); paint.style=Paint.Style.FILL
        oval(0x77FFFFFF, x-h*.115f,y-h*.32f,x+h*.115f,y-h*.14f)
        for (side in listOf(-1,1)) {
            oval(Pop.ink,x+side*h*.098f-h*.018f,y+h*.015f,x+side*h*.098f+h*.018f,y+h*.085f)
            oval(0x88FFFFFF.toInt(),x+side*h*.16f-h*.04f,y+h*.108f,x+side*h*.16f+h*.04f,y+h*.153f)
        }
        paint.color=Pop.ink; paint.style=Paint.Style.STROKE; paint.strokeWidth=h*.018f
        canvas.drawArc(RectF(x-h*.055f,y+h*.105f,x+h*.055f,y+h*.195f),0f,180f,false,paint)
        paint.style=Paint.Style.FILL
        if(index==3 || index==4) { paint.textSize=h*.17f; paint.color=if(index==3) Pop.purple else Pop.yellow; canvas.drawText(if(index==3) "❧" else "★",x+h*.14f,y-h*.24f,paint) }
    }
}
