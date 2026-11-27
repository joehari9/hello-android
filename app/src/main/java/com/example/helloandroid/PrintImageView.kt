package com.example.helloandroid

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class PrintImageView(
    private val activity: MainActivity,
    private val onBackToHome: () -> Unit,
    private val onPickImage: () -> Unit,
    private val onPrintImageBitmap: (Bitmap) -> Unit
) {
    private var selectedBitmap: Bitmap? = null
    private var selectedFileName: String = ""

    private lateinit var infoLabel: TextView
    private lateinit var widthBadgeLabel: TextView
    private lateinit var previewImageView: ImageView
    private lateinit var printButton: TextView

    private fun resolveTargetDotWidth(): Int {
        val name = activity.getConnectedPrinterNameForAutoWidth().lowercase(Locale.getDefault())
        return if (name.contains("80") || name.contains("576") || name.contains("72mm")) {
            576
        } else {
            384
        }
    }

    internal fun handleSelectedImage(uri: Uri) {
        try {
            val resolver = activity.contentResolver
            val displayName = resolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            } ?: (uri.lastPathSegment ?: "gambar")

            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }

            val srcW = boundsOpts.outWidth.coerceAtLeast(1)
            val srcH = boundsOpts.outHeight.coerceAtLeast(1)
            val maxDecodeDim = 1600
            var sampleSize = 1
            while ((srcW / sampleSize) > maxDecodeDim || (srcH / sampleSize) > maxDecodeDim) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decoded = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }

            if (decoded == null) {
                Toast.makeText(activity, "Gagal memuat gambar", Toast.LENGTH_SHORT).show()
                return
            }

            selectedBitmap?.let {
                if (!it.isRecycled) it.recycle()
            }
            selectedBitmap = decoded
            selectedFileName = displayName

            refreshPreview()
            Toast.makeText(activity, "Gambar dimuat: $displayName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(activity, "Error baca gambar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshPreview() {
        val dotWidth = resolveTargetDotWidth()
        val innerWidth = (dotWidth - 32).coerceAtLeast(120)
        widthBadgeLabel.text = "Auto ${dotWidth}px (${innerWidth}px area)"

        val bmp = selectedBitmap
        if (bmp == null || bmp.isRecycled) {
            infoLabel.text = "Belum ada gambar atau logo dipilih"
            previewImageView.setImageBitmap(null)
            printButton.alpha = 0.5f
            return
        }

        val fitted = activity.fitBitmapToThermalWidth(bmp, dotWidth, 16)
        infoLabel.text = "$selectedFileName  •  Asli: ${bmp.width}x${bmp.height}px  •  Cetak: ${fitted.width}x${fitted.height}px"
        previewImageView.setImageBitmap(fitted)
        printButton.alpha = 1f
    }

    fun buildView(): View {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(18), activity.dp(8), activity.dp(18), activity.dp(28))
            setBackgroundColor(activity.colBgPrimary)
        }

        val topBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, activity.dp(12))
        }

        val backBtn = TextView(activity).apply {
            text = "← Kembali"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(activity.colAccentCyan)
            setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8))
            background = activity.rippleRounded(activity.colSurface, 12, activity.colBorder, 1)
            isClickable = true
            isFocusable = true
            setOnClickListener { onBackToHome() }
        }

        widthBadgeLabel = TextView(activity).apply {
            text = "Auto 384px"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(activity.colAccentCyan)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        topBar.addView(backBtn)
        topBar.addView(widthBadgeLabel)
        container.addView(topBar)

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(14), activity.dp(14), activity.dp(14), activity.dp(14))
            background = activity.rounded(activity.colSurface, 18, activity.colBorder, 1)
            elevation = activity.dp(2).toFloat()
        }

        card.addView(TextView(activity).apply {
            text = "PRINT IMAGE / LOGO OTOMATIS"
            textSize = 11f
            letterSpacing = 0.08f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(activity.colAccentCyan)
            setPadding(0, 0, 0, activity.dp(8))
        })

        val pickBtn = TextView(activity).apply {
            text = "PILIH GAMBAR / LOGO"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(activity.dp(12), activity.dp(11), activity.dp(12), activity.dp(11))
            background = activity.rippleGradient(activity.colAccentBlue, activity.colAccentCyan, 12)
            isClickable = true
            isFocusable = true
            setOnClickListener { onPickImage() }
        }

        infoLabel = TextView(activity).apply {
            text = "Belum ada gambar atau logo dipilih"
            textSize = 11.5f
            setTextColor(activity.colTextSecondary)
            setPadding(0, activity.dp(8), 0, activity.dp(10))
        }

        previewImageView = ImageView(activity).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8))
        }

        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val clearBtn = TextView(activity).apply {
            text = "BERSIHKAN"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(activity.colTextPrimary)
            setPadding(activity.dp(10), activity.dp(12), activity.dp(10), activity.dp(12))
            background = activity.rippleRounded(activity.colSurfaceElevated, 12, activity.colBorder, 1)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                selectedBitmap?.let {
                    if (!it.isRecycled) it.recycle()
                }
                selectedBitmap = null
                selectedFileName = ""
                refreshPreview()
            }
        }

        printButton = TextView(activity).apply {
            text = "CETAK GAMBAR"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(activity.dp(12), activity.dp(12), activity.dp(12), activity.dp(12))
            background = activity.rippleGradient(activity.colSuccess, activity.colAccentCyan, 12)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val bmp = selectedBitmap
                if (bmp == null || bmp.isRecycled) {
                    Toast.makeText(activity, "Pilih gambar atau logo terlebih dahulu", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                onPrintImageBitmap(bmp)
            }
        }

        btnRow.addView(clearBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.35f).apply {
            rightMargin = activity.dp(6)
        })
        btnRow.addView(printButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.65f).apply {
            leftMargin = activity.dp(6)
        })

        card.addView(pickBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        card.addView(infoLabel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        card.addView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = activity.rounded(Color.WHITE, 10, activity.colBorder, 1)
                setPadding(activity.dp(4), activity.dp(4), activity.dp(4), activity.dp(4))
                addView(previewImageView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = activity.dp(12)
            }
        )
        card.addView(btnRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        container.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        refreshPreview()
        return container
    }
}
