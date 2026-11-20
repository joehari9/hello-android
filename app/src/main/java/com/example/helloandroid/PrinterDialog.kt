
package com.example.helloandroid

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

object PrinterDialog {

    fun show(context: Context) {

        val dialog = Dialog(context)

        fun dp(value: Int): Int {
            return (value * context.resources.displayMetrics.density).toInt()
        }

        fun roundedBackground(
            color: Int,
            radius: Int
        ): GradientDrawable {
            return GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(radius).toFloat()
            }
        }

        val navy = Color.rgb(23, 54, 92)
        val muted = Color.rgb(100, 116, 139)
        val blue = Color.rgb(23, 105, 210)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(20))
            background = roundedBackground(Color.WHITE, 22)
        }

        // Ikon printer
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_printer)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        root.addView(
            icon,
            LinearLayout.LayoutParams(dp(68), dp(68))
        )

        // Judul
        val title = TextView(context).apply {
            text = "Printer Information"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(navy)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }

        root.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // Informasi koneksi
        val usbConnected =
            MainActivityHolder.isUsbConnected(context)

        dialog.setContentView(root)

        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
    }
}
