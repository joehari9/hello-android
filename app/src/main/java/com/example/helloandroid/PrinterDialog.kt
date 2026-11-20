
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
import android.view.View

object PrinterDialog {

    fun show(
        context: Context,
        printerName: String,
        connectionType: String,
        connected: Boolean
    ) {

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
        val appIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_printer)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        root.addView(
            appIcon,
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

        // Nama printer
        val name = TextView(context).apply {
            text = printerName
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(navy)
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, dp(12))
        }

        root.addView(
            name,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // Jenis koneksi
        val connection = TextView(context).apply {
            text = "Connection Type: $connectionType"
            textSize = 13f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(10))
        }

        root.addView(
            connection,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // Status koneksi
        val statusText = TextView(context).apply {
            text = if (connected) "● Connected" else "● Disconnected"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(
                if (connected) Color.rgb(22, 134, 83)
                else Color.rgb(200, 55, 55)
            )
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(20))
        }

        root.addView(
            statusText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // Garis pemisah
        val divider = View(context).apply {
            setBackgroundColor(Color.rgb(220, 227, 236))
        }

        root.addView(
            divider,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
            )
        )

        // Tombol CLOSE
        val closeButton = Button(context).apply {
            text = "CLOSE"
            isAllCaps = false
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = roundedBackground(blue, 12)
            setOnClickListener {
                dialog.dismiss()
            }
        }

        val buttonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(48)
        ).apply {
            topMargin = dp(18)
        }

        root.addView(closeButton, buttonParams)

        dialog.setContentView(root)

        dialog.setCanceledOnTouchOutside(true)
        dialog.show()

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                dp(320),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }
}
