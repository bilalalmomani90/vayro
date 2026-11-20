package com.vayro.browser.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.vayro.browser.R

/**
 * يضيف موقعاً إلى الشاشة الرئيسية للهاتف كاختصار يشبه التطبيق.
 * الاختصار يفتح الموقع داخل VAYRO في تبويب جديد (تجربة "تطبيق" بحسب إمكانيات النظام).
 * الأيقونة حرف أول على تدرج الهوية، ولا تُجلب أي أيقونة من الإنترنت.
 */
object PinnedShortcuts {

    /** يطلب من النظام إضافة الاختصار؛ يعيد false إذا كانت الشاشة الرئيسية لا تدعم ذلك. */
    fun pin(context: Context, url: String, label: String): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse(url))
        val info = ShortcutInfoCompat.Builder(context, "site_${url.hashCode()}")
            .setShortLabel(label.take(MAX_SHORT_LABEL))
            .setLongLabel(label)
            .setIcon(IconCompat.createWithAdaptiveBitmap(icon(context, label)))
            .setIntent(intent)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }

    private fun icon(context: Context, label: String): Bitmap {
        val size = (ICON_DP * context.resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // خلفية مربعة كاملة: النظام يقصّها بالشكل المناسب (دائرة/مربع مدوّر)
        paint.shader = LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            ContextCompat.getColor(context, R.color.vayro_primary),
            ContextCompat.getColor(context, R.color.vayro_secondary),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.shader = null
        paint.color = Color.WHITE
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size * 0.38f // داخل المنطقة الآمنة للأيقونات التكيفية
        val letter = label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
        val baseline = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(letter, size / 2f, baseline, paint)
        return bitmap
    }

    private const val ICON_DP = 108
    private const val MAX_SHORT_LABEL = 24
}
