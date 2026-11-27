package com.example.helloandroid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import android.widget.Toast

class PrintReceiptView(
    private val activity: MainActivity,
    private val onBackToHome: () -> Unit,
    private val onPickPdf: () -> Unit,
    private val onPrintBitmap: (Bitmap) -> Unit
) {

    private var selectedPaperWidthPx: Int = 384 // Default 58mm = 384 px (80mm = 576 px)
    private var lastSourceBitmap: Bitmap? = null
    private var processedBitmap: Bitmap? = null

    private lateinit var paper58Btn: TextView
    private lateinit var paper80Btn: TextView
    private lateinit var resolutionBadge: TextView
    private lateinit var pdfPreview: ImageView
    private lateinit var previewPlaceholder: LinearLayout

    fun buildView(): View {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(18), activity.dp(8), activity.dp(18), activity.dp(24))
            setBackgroundColor(activity.colBgPrimary)
        }

        // Baris Navigasi Kembali & Judul Sub-Menu
        val subHeader = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, activity.dp(12))
        }

        val backBtn = TextView(activity).apply {
            text = "← Kembali"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(activity.colAccentCyan)
            setPadding(activity.dp(12), activity.dp(7), activity.dp(12), activity.dp(7))
            background = activity.rippleRounded(
                activity.colSurfaceElevated,
                10,
                activity.colBorder,
                1
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onBackToHome() }
        }

        val subTitle = TextView(activity).apply {
            text = "PRINT RECEIPT"
            textSize = 13f
            letterSpacing = 0.06f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(activity.colTextPrimary)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        subHeader.addView(backBtn)
        subHeader.addView(subTitle)
        container.addView(subHeader)

        // Kartu Pilihan Lebar Kertas Thermal (58mm / 80mm)
        val paperCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12))
            background = activity.rounded(activity.colSurface, 16, activity.colBorder, 1)
        }

        paperCard.addView(
            TextView(activity).apply {
                text = "UKURAN KERTAS THERMAL"
                textSize = 11f
                letterSpacing = 0.06f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(activity.colTextSecondary)
                setPadding(0, 0, 0, activity.dp(8))
            }
        )

        val paperRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        paper58Btn = TextView(activity).apply {
            text = "58 mm (384 px)"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(activity.dp(8), activity.dp(10), activity.dp(8), activity.dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { selectPaperWidth(384) }
        }

        paper80Btn = TextView(activity).apply {
            text = "80 mm (576 px)"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(activity.dp(8), activity.dp(10), activity.dp(8), activity.dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { selectPaperWidth(576) }
        }

        paperRow.addView(
            paper58Btn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = activity.dp(6)
            }
        )
        paperRow.addView(
            paper80Btn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = activity.dp(6)
            }
        )

        paperCard.addView(paperRow)
        container.addView(
            paperCard,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = activity.dp(18)
            }
        )

        // Header Preview + Badge Dimensi
        val previewHeader = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(2), 0, activity.dp(2), activity.dp(10))
        }

        previewHeader.addView(
            TextView(activity).apply {
                text = "RECEIPT PREVIEW"
                textSize = 12f
                letterSpacing = 0.08f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(activity.colTextSecondary)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
        )

        resolutionBadge = TextView(activity).apply {
            text = "384 PX • 1-BIT MONO"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(activity.colAccentCyan)
            setPadding(activity.dp(8), activity.dp(3), activity.dp(8), activity.dp(3))
            background = activity.rounded(activity.colSurfaceElevated, 8, activity.colBorder, 1)
        }
        previewHeader.addView(resolutionBadge)
        container.addView(previewHeader)

        // Workspace & Kertas Preview
        val previewWorkspace = FrameLayout(activity).apply {
            background = activity.rounded(activity.colSurface, 20, activity.colBorder, 1)
            setPadding(activity.dp(14), activity.dp(14), activity.dp(14), activity.dp(14))
            elevation = activity.dp(4).toFloat()
        }

        val previewPanel = FrameLayout(activity).apply {
            background = activity.rounded(
                Color.parseColor("#F8FAFC"),
                14,
                Color.parseColor("#CBD5E1"),
                1
            )
            minimumHeight = activity.dp(250)
            setPadding(activity.dp(14), activity.dp(16), activity.dp(14), activity.dp(16))
        }

        previewPlaceholder = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val iconCircle = FrameLayout(activity).apply {
            background = activity.rounded(Color.parseColor("#E2E8F0"), 28)
        }
        iconCircle.addView(
            TextView(activity).apply {
                text = "🧾"
                textSize = 28f
                gravity = Gravity.CENTER
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        previewPlaceholder.addView(
            iconCircle,
            LinearLayout.LayoutParams(activity.dp(60), activity.dp(60)).apply {
                bottomMargin = activity.dp(12)
            }
        )

        previewPlaceholder.addView(
            TextView(activity).apply {
                text = "Belum Ada Resi Dipilih"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#1E293B"))
                gravity = Gravity.CENTER
            }
        )

        previewPlaceholder.addView(
            TextView(activity).apply {
                text = "Pilih file PDF resi untuk melihat pratinjau cetak thermal"
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
                gravity = Gravity.CENTER
                setPadding(activity.dp(16), activity.dp(4), activity.dp(16), activity.dp(14))
            }
        )

        for (w in listOf(160, 130, 145, 95)) {
            previewPlaceholder.addView(
                View(activity).apply {
                    background = activity.rounded(Color.parseColor("#CBD5E1"), 4)
                },
                LinearLayout.LayoutParams(activity.dp(w), activity.dp(6)).apply {
                    topMargin = activity.dp(7)
                }
            )
        }

        previewPanel.addView(
            previewPlaceholder,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        pdfPreview = ImageView(activity).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
        }

        previewPanel.addView(
            pdfPreview,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        previewWorkspace.addView(
            previewPanel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        container.addView(
            previewWorkspace,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = activity.dp(18)
            }
        )

        // Tombol Select PDF
        val selectPdfButton = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = activity.rippleRounded(
                activity.colSurfaceElevated,
                16,
                activity.colAccentCyan,
                1
            )
            isClickable = true
            isFocusable = true
            setPadding(activity.dp(16), activity.dp(4), activity.dp(16), activity.dp(4))
            minimumHeight = activity.dp(54)

            addView(ImageView(activity).apply {
                setImageResource(R.drawable.ic_pdf)
                setColorFilter(activity.colAccentCyan)
                layoutParams = LinearLayout.LayoutParams(activity.dp(24), activity.dp(24))
            })
            addView(Space(activity).apply {
                layoutParams = LinearLayout.LayoutParams(activity.dp(10), 1)
            })
            addView(TextView(activity).apply {
                text = "SELECT RECEIPT PDF"
                textSize = 14f
                letterSpacing = 0.04f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(activity.colTextPrimary)
                gravity = Gravity.CENTER
                includeFontPadding = false
            })

            setOnClickListener { onPickPdf() }
        }

        container.addView(
            selectPdfButton,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(54))
        )

        // Tombol Print
        val printButton = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = activity.rippleGradient(
                Color.parseColor("#059669"),
                Color.parseColor("#10B981"),
                16
            )
            elevation = activity.dp(6).toFloat()
            isClickable = true
            isFocusable = true
            setPadding(activity.dp(16), activity.dp(4), activity.dp(16), activity.dp(4))
            minimumHeight = activity.dp(58)

            addView(ImageView(activity).apply {
                setImageResource(R.drawable.ic_printer)
                setColorFilter(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(activity.dp(26), activity.dp(26))
            })
            addView(Space(activity).apply {
                layoutParams = LinearLayout.LayoutParams(activity.dp(10), 1)
            })
            addView(TextView(activity).apply {
                text = "PRINT THERMAL RECEIPT"
                textSize = 15f
                letterSpacing = 0.04f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                includeFontPadding = false
            })

            setOnClickListener {
                val bmp = processedBitmap
                if (bmp == null) {
                    activity.updateStatusText("Pilih PDF resi terlebih dahulu")
                    Toast.makeText(activity, "Pilih PDF resi terlebih dahulu", Toast.LENGTH_SHORT).show()
                } else {
                    onPrintBitmap(bmp)
                }
            }
        }

        container.addView(
            printButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                activity.dp(58)
            ).apply {
                topMargin = activity.dp(12)
            }
        )

        // Sinkronkan tampilan pilihan kertas awal (58mm)
        refreshPaperSelectorUi()

        return container
    }

    private fun selectPaperWidth(widthPx: Int) {
        selectedPaperWidthPx = widthPx
        refreshPaperSelectorUi()

        val src = lastSourceBitmap
        if (src != null) {
            val result = processReceipt(src, selectedPaperWidthPx)
            replaceProcessedBitmap(result)
            pdfPreview.setImageBitmap(result)
            previewPlaceholder.visibility = View.GONE
            pdfPreview.visibility = View.VISIBLE
            resolutionBadge.text = "${result.width} x ${result.height} PX"
            activity.updateStatusText("Diperbarui ke ${selectedPaperWidthPx} px (${result.width}x${result.height})")
        } else {
            resolutionBadge.text = "$selectedPaperWidthPx PX • 1-BIT MONO"
        }
    }

    private fun refreshPaperSelectorUi() {
        val is58 = selectedPaperWidthPx == 384

        paper58Btn.background = if (is58) {
            activity.rippleRounded(activity.colAccentBlue, 10, activity.colAccentCyan, 1)
        } else {
            activity.rippleRounded(activity.colSurfaceElevated, 10, activity.colBorder, 1)
        }
        paper58Btn.setTextColor(if (is58) Color.WHITE else activity.colTextSecondary)

        paper80Btn.background = if (!is58) {
            activity.rippleRounded(activity.colAccentBlue, 10, activity.colAccentCyan, 1)
        } else {
            activity.rippleRounded(activity.colSurfaceElevated, 10, activity.colBorder, 1)
        }
        paper80Btn.setTextColor(if (!is58) Color.WHITE else activity.colTextSecondary)
    }

    fun handleSelectedPdf(uri: Uri) {
        try {
            activity.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount == 0) {
                        throw Exception("PDF tidak memiliki halaman")
                    }

                    renderer.openPage(0).use { page ->
                        val renderWidth = (page.width * 150f / 72f).toInt()
                        val renderHeight = (page.height * 150f / 72f).toInt()

                        val sourceBitmap = Bitmap.createBitmap(
                            renderWidth,
                            renderHeight,
                            Bitmap.Config.ARGB_8888
                        )

                        page.render(
                            sourceBitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )

                        lastSourceBitmap?.let { old ->
                            if (!old.isRecycled) old.recycle()
                        }
                        lastSourceBitmap = sourceBitmap

                        val result = processReceipt(sourceBitmap, selectedPaperWidthPx)
                        replaceProcessedBitmap(result)

                        pdfPreview.setImageBitmap(result)
                        previewPlaceholder.visibility = View.GONE
                        pdfPreview.visibility = View.VISIBLE

                        resolutionBadge.text = "${result.width} x ${result.height} PX"
                        activity.updateStatusText(
                            "Resi siap dicetak (${result.width} x ${result.height} px)"
                        )
                    }
                }
            } ?: throw Exception("Tidak dapat membuka file PDF")
        } catch (e: Exception) {
            activity.updateStatusText("Gagal memproses PDF: ${e.message}")
        }
    }

    private fun replaceProcessedBitmap(newBitmap: Bitmap) {
        val old = processedBitmap
        processedBitmap = newBitmap
        if (old != null && old !== newBitmap && !old.isRecycled) {
            old.recycle()
        }
    }

    private fun processReceipt(source: Bitmap, targetWidth: Int): Bitmap {
        return if (targetWidth == 576) {
            process80mm(source, targetWidth)
        } else {
            process58mm(source, 384)
        }
    }

    // Logika 58mm dipertahankan agar tidak berubah dari versi yang sudah jalan
    private fun process58mm(source: Bitmap, targetWidth: Int): Bitmap {
        val cropX = 0
        val cropY = 35
        val cropWidth = source.width
        val cropHeight = minOf(450, source.height - cropY)

        require(cropHeight > 0) {
            "Ukuran PDF terlalu kecil untuk crop"
        }

        val cropped = Bitmap.createBitmap(source, cropX, cropY, cropWidth, cropHeight)
        val trimmed = trimWhitespace(cropped)
        if (trimmed !== cropped) cropped.recycle()

        val rotated = rotateBitmap(trimmed, -90f)
        if (rotated !== trimmed) trimmed.recycle()

        activity.updateStatusText(
            "58mm Trim: ${trimmed.width}x${trimmed.height} | Rotasi: ${rotated.width}x${rotated.height}"
        )

        val newHeight = (rotated.height * targetWidth.toFloat() / rotated.width)
            .toInt()
            .coerceAtLeast(1)

        val resized = Bitmap.createScaledBitmap(rotated, targetWidth, newHeight, true)
        if (resized !== rotated) rotated.recycle()

        val binary = toMonochromeBitmap(resized, threshold = 255 * 0.65)
        if (binary !== resized) resized.recycle()

        val finalRotation = rotateBitmap(binary, 180f)
        if (finalRotation !== binary) binary.recycle()

        return addSideBorders(finalRotation, targetWidth)
    }

    // Logika 80mm: struktur sama seperti 58mm (crop -> trim -> rotasi -90 -> resize ke lebar 576px -> mono -> rotasi 180 -> border)
    private fun process80mm(source: Bitmap, targetWidth: Int): Bitmap {
        val cropX = 0
        val cropY = 35
        val cropWidth = source.width
        val cropHeight = minOf(450, source.height - cropY)

        require(cropHeight > 0) {
            "Ukuran PDF terlalu kecil untuk crop 80mm"
        }

        val cropped = Bitmap.createBitmap(source, cropX, cropY, cropWidth, cropHeight)
        val trimmed = trimWhitespace(cropped)
        if (trimmed !== cropped) cropped.recycle()

        val rotated = rotateBitmap(trimmed, -90f)
        if (rotated !== trimmed) trimmed.recycle()

        activity.updateStatusText(
            "80mm Trim: ${trimmed.width}x${trimmed.height} | Rotasi: ${rotated.width}x${rotated.height}"
        )

        val newHeight = (rotated.height * targetWidth.toFloat() / rotated.width)
            .toInt()
            .coerceAtLeast(1)

        val resized = Bitmap.createScaledBitmap(rotated, targetWidth, newHeight, true)
        if (resized !== rotated) rotated.recycle()

        val binary = toMonochromeBitmap(resized, threshold = 255 * 0.65)
        if (binary !== resized) resized.recycle()

        val finalRotation = rotateBitmap(binary, 180f)
        if (finalRotation !== binary) binary.recycle()

        return addSideBorders(finalRotation, targetWidth)
    }

    private fun trimWhitespace(cropped: Bitmap): Bitmap {
        val rowCounts = IntArray(cropped.height)
        val colCounts = IntArray(cropped.width)

        for (y in 0 until cropped.height) {
            for (x in 0 until cropped.width) {
                val pixel = cropped.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                if (r < 250 || g < 250 || b < 250) {
                    rowCounts[y]++
                    colCounts[x]++
                }
            }
        }

        var minY = cropped.height
        var maxY = -1
        var minX = cropped.width
        var maxX = -1

        for (y in rowCounts.indices) {
            if (rowCounts[y] > 2 && rowCounts[y] < cropped.width * 0.80) {
                minY = minOf(minY, y)
                maxY = maxOf(maxY, y)
            }
        }

        for (x in colCounts.indices) {
            if (colCounts[x] > 2 && colCounts[x] < cropped.height * 0.80) {
                minX = minOf(minX, x)
                maxX = maxOf(maxX, x)
            }
        }

        require(maxX >= minX && maxY >= minY) {
            "Isi resi tidak ditemukan"
        }

        return Bitmap.createBitmap(
            cropped,
            minX,
            minY,
            maxX - minX + 1,
            maxY - minY + 1
        )
    }

    private fun toMonochromeBitmap(source: Bitmap, threshold: Double): Bitmap {
        val width = source.width
        val height = source.height
        val binary = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = source.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val gray = (0.299 * r + 0.587 * g + 0.114 * b)
                val value = if (gray < threshold) 0 else 255
                binary.setPixel(x, y, Color.rgb(value, value, value))
            }
        }
        return binary
    }

    private fun addSideBorders(contentBitmap: Bitmap, targetWidth: Int): Bitmap {
        val result = Bitmap.createBitmap(targetWidth, contentBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val paint = Paint()
        canvas.drawBitmap(contentBitmap, 0f, 0f, paint)

        val borderPaint = Paint().apply {
            color = Color.BLACK
        }

        val rightBorderStart = (targetWidth - 2).toFloat().coerceAtLeast(0f)
        canvas.drawRect(0f, 0f, 2f, result.height.toFloat(), borderPaint)
        canvas.drawRect(rightBorderStart, 0f, targetWidth.toFloat(), result.height.toFloat(), borderPaint)

        return result
    }

    private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply {
            postRotate(degrees)
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}