
package com.example.helloandroid

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object HelpDialog {

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
            setPadding(dp(20), dp(20), dp(20), dp(18))
            background = roundedBackground(Color.WHITE, 22)
        }

        // Ikon Help
        val icon = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_help)
            setColorFilter(blue)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        root.addView(
            icon,
            LinearLayout.LayoutParams(dp(48), dp(48))
        )

        // Judul
        val title = TextView(context).apply {
            text = "Help & User Guide"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(navy)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(18))
        }

        root.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // Area panduan yang bisa di-scroll
        val scrollView = ScrollView(context).apply {
            isFillViewport = false
        }

        val guideLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Data panduan
        val guides = listOf(
            Pair(
                "How to connect printer via USB",
                listOf(
                    "1. Hubungkan printer ke HP menggunakan kabel USB OTG.",
                    "2. Berikan izin USB ketika diminta Android.",
                    "3. Tunggu sampai status printer menunjukkan Connected."
                )
            ),
            Pair(
                "How to connect printer via Bluetooth",
                listOf(
                    "1. Aktifkan Bluetooth pada HP dan printer.",
                    "2. Tekan SCAN DEVICE untuk mencari printer.",
                    "3. Pilih printer yang ditemukan.",
                    "4. Tekan CONNECT dan tunggu hingga terhubung."
                )
            ),
            Pair(
                "How to select PDF",
                listOf(
                    "1. Tekan SELECT PDF.",
                    "2. Pilih dokumen PDF dari penyimpanan HP.",
                    "3. Periksa hasilnya pada preview."
                )
            ),
            Pair(
                "How to print receipt",
                listOf(
                    "1. Pastikan printer sudah terhubung.",
                    "2. Pastikan preview dokumen sudah benar.",
                    "3. Tekan PRINT untuk mulai mencetak."
                )
            )
        )

        var expandedIndex = -1

        guides.forEachIndexed { index, guide ->

            val section = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedBackground(
                    Color.rgb(245, 248, 252),
                    12
                )
            }

            // Judul panduan dan panah
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                isClickable = true
                isFocusable = true
            }

            val headerText = TextView(context).apply {
                text = guide.first
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(navy)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val arrow = TextView(context).apply {
                text = "▼"
                textSize = 14f
                setTextColor(blue)
            }

            header.addView(headerText)
            header.addView(arrow)

            // Isi panduan
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    dp(14),
                    0,
                    dp(14),
                    dp(16)
                )
                visibility = View.GONE
            }

            guide.second.forEach { instruction ->

                val instructionText = TextView(context).apply {
                    text = instruction
                    textSize = 13f
                    setTextColor(muted)
                    setPadding(0, dp(5), 0, dp(5))
                }

                content.addView(
                    instructionText,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }

            header.setOnClickListener {

                if (expandedIndex == index) {
                    expandedIndex = -1
                } else {
                    expandedIndex = index
                }

                for (i in 0 until guideLayout.childCount) {

                    val item = guideLayout.getChildAt(i)
                        as LinearLayout

                    val itemHeader = item.getChildAt(0)
                        as LinearLayout

                    val itemArrow = itemHeader.getChildAt(1)
                        as TextView

                    val itemContent = item.getChildAt(1)

                    val isExpanded = i == expandedIndex

                    itemContent.visibility =
                        if (isExpanded) View.VISIBLE else View.GONE

                    itemArrow.text =
                        if (isExpanded) "▲" else "▼"
                }
            }

            section.addView(header)
            section.addView(content)

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }

            guideLayout.addView(section, params)
        }

        scrollView.addView(guideLayout)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
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
            topMargin = dp(14)
        }

        root.addView(closeButton, buttonParams)

        dialog.setContentView(root)

        dialog.setCanceledOnTouchOutside(true)
        dialog.show()

        dialog.window?.apply {
            setBackgroundDrawableResource(
                android.R.color.transparent
            )
            setLayout(
                dp(340),
                dp(540)
            )
        }
    }
}
