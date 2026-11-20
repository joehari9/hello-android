
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

object AboutDialog {

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

        // Layout utama
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(20))
            background = roundedBackground(Color.WHITE, 22)
        }

        // Ikon aplikasi
        val appIcon = ImageView(context).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        root.addView(
            appIcon,
            LinearLayout.LayoutParams(dp(68), dp(68))
        )

        // Nama aplikasi
        val title = TextView(context).apply {
            text = "JZ Receipt Printer"
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

        // Versi aplikasi
        val version = context.packageManager
            .getPackageInfo(context.packageName, 0).versionName ?: "1.0"

        val versionText = TextView(context).apply {
            text = "Version $version"
            textSize = 12f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, 0)
        }

        root.addView(
            versionText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // Deskripsi
        val description = TextView(context).apply {
            text = "Aplikasi pencetak struk thermal melalui Bluetooth dan USB."
            textSize = 14f
            setTextColor(Color.rgb(51, 65, 85))
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(20))
        }

        root.addView(
            description,
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

        // Pengembang
        val developer = TextView(context).apply {
            text = "Developed by Joe Hari"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(navy)
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(5))
        }

        root.addView(
            developer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // Watermark
        val watermark = TextView(context).apply {
            text = "Generate by: JZ Receipt Printer"
            textSize = 11f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(18))
        }

        root.addView(
            watermark,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
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

        root.addView(
            closeButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        dialog.setContentView(root)

        dialog.window?.apply {
            setBackgroundDrawableResource(
                android.R.color.transparent
            )
            setLayout(
                dp(320),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addFlags(
                android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )
            setDimAmount(0.55f)
        }

        dialog.setCanceledOnTouchOutside(true)
        dialog.show()

        dialog.window?.setLayout(
            dp(320),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
