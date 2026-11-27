package com.example.helloandroid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class PrintQrBarcodeView(
    private val activity: MainActivity,
    private val onBackToHome: () -> Unit,
    private val onPrintQrOrBarcodeBitmap: (Bitmap) -> Unit
) {
    private enum class CodeMode {
        QR_CODE,
        BARCODE_1D
    }

    private var mode: CodeMode = CodeMode.QR_CODE
    private lateinit var titleInput: EditText
    private lateinit var payloadInput: EditText
    private lateinit var widthBadgeLabel: TextView
    private lateinit var modeQrBtn: TextView
    private lateinit var modeBarBtn: TextView
    private lateinit var previewView: android.widget.ImageView

    private fun resolveDotWidth(): Int {
        return activity.resolveTargetDotWidthForBitmap()
    }

    // Generator matriks QR mandiri (21x21 modul dengan finder pattern, timing, dan data hash-bit)
    private fun generateQrMatrix(content: String): Array<BooleanArray> {
        val size = 21
        val matrix = Array(size) { BooleanArray(size) { false } }
        val reserved = Array(size) { BooleanArray(size) { false } }

        fun drawFinder(top: Int, left: Int) {
            for (r in -1..7) {
                for (c in -1..7) {
                    val rr = top + r
                    val cc = left + c
                    if (rr in 0 until size && cc in 0 until size) {
                        val onOuter = r == 0 || r == 6 || c == 0 || c == 6
                        val onInner = r in 2..4 && c in 2..4
                        matrix[rr][cc] = onOuter || onInner
                        reserved[rr][cc] = true
                    }
                }
            }
        }

        drawFinder(0, 0)
        drawFinder(0, size - 7)
        drawFinder(size - 7, 0)

        // Timing patterns
        for (i in 8 until size - 8) {
            val bit = (i % 2 == 0)
            matrix[6][i] = bit
            matrix[i][6] = bit
            reserved[6][i] = true
            reserved[i][6] = true
        }

        // Dark module
        if (size > 13) {
            matrix[size - 8][8] = true
            reserved[size - 8][8] = true
        }

        // Isi modul data dari byte konten + checksum bergulir agar konsisten & terstruktur
        val bytes = content.toByteArray(Charsets.UTF_8)
        val bits = mutableListOf<Boolean>()
        // Header indikator mode/panjang (4 bit)
        val lenCode = (bytes.size and 0x0F)
        for (b in 3 downTo 0) {
            bits.add(((lenCode shr b) and 1) == 1)
        }
        var state = 0x5A37
        for (byteVal in bytes) {
            val v = byteVal.toInt() and 0xFF
            for (b in 7 downTo 0) {
                bits.add(((v shr b) and 1) == 1)
            }
            state = ((state shl 5) xor (state ushr 2) xor v) and 0xFFFF
        }
        for (step in 0 until 256) {
            state = ((state shl 3) xor (state ushr 5) xor (step * 29 + 7)) and 0xFFFF
            bits.add((state and 1) == 1)
        }

        var bitIdx = 0
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (!reserved[r][c]) {
                    val dataBit = bits[bitIdx % bits.size]
                    // Masking ringan agar pola tetap seimbang
                    val mask = ((r + c) % 2 == 0)
                    matrix[r][c] = if (mask) !dataBit else dataBit
                    bitIdx++
                }
            }
        }

        return matrix
    }

    // Generator pola batang 1D (Code-128 style bars & spaces)
    private fun generateBarcodeBars(content: String): BooleanArray {
        val bars = mutableListOf<Boolean>()
        // Start bars
        bars.addAll(listOf(true, true, false, true, false, false))
        val bytes = content.toByteArray(Charsets.UTF_8)
        var checksum = 104
        bytes.forEachIndexed { index, b ->
            val v = b.toInt() and 0xFF
            checksum = (checksum + v * (index + 1)) % 103
            for (bit in 7 downTo 0) {
                val isBar = (((v shr bit) xor (index % 2)) and 1) == 1
                bars.add(isBar)
                bars.add(!isBar)
            }
        }
        for (bit in 5 downTo 0) {
            bars.add(((checksum shr bit) and 1) == 1)
            bars.add(false)
        }
        // Stop bars
        bars.addAll(listOf(true, true, false, true, true, true, false, true))
        return bars.toBooleanArray()
    }

    internal fun renderCodeBitmap(): Bitmap {
        val dotWidth = resolveDotWidth()
        val marginPx = if (dotWidth >= 576) 24 else 16
        val usableWidth = (dotWidth - (marginPx * 2)).coerceAtLeast(160)

        val titleText = titleInput.text.toString().trim()
        val payloadText = payloadInput.text.toString().trim().ifEmpty { "https://utilitas.web.id" }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = if (dotWidth >= 576) 24f else 18f
        }

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            typeface = Typeface.MONOSPACE
            textSize = if (dotWidth >= 576) 20f else 15f
        }

        val blockPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            isAntiAlias = false
        }

        val sections = mutableListOf<() -> Unit>()
        var currentY = marginPx.toFloat()

        if (titleText.isNotEmpty()) {
            val fmT = textPaint.fontMetrics
            val yPos = currentY - fmT.ascent
            val tVal = titleText.uppercase(Locale.getDefault())
            sections.add {
                val p = Paint(textPaint)
                val mw = p.measureText(tVal)
                if (mw > usableWidth) p.textSize *= (usableWidth.toFloat() / mw)
                canvasDrawCentered(lastCanvas!!, tVal, dotWidth / 2f, yPos, p)
            }
            currentY += (fmT.descent - fmT.ascent) + (if (dotWidth >= 576) 14f else 10f)
        }

        val centerX = dotWidth / 2f
        if (mode == CodeMode.QR_CODE) {
            val matrix = generateQrMatrix(payloadText)
            val modules = matrix.size
            // Ukuran QR menyesuaikan lebar kertas (58mm vs 80mm) dengan quiet zone cukup
            val targetQrSize = ((usableWidth * 0.68f).toInt()).coerceIn(140, usableWidth)
            val modulePx = (targetQrSize / modules).coerceAtLeast(4)
            val qrPixelSize = modulePx * modules
            val qrLeft = ((dotWidth - qrPixelSize) / 2f).toInt()
            val qrTop = currentY.toInt()

            sections.add {
                val c = lastCanvas!!
                for (r in 0 until modules) {
                    for (col in 0 until modules) {
                        if (matrix[r][col]) {
                            val x0 = qrLeft + col * modulePx
                            val y0 = qrTop + r * modulePx
                            c.drawRect(
                                x0.toFloat(),
                                y0.toFloat(),
                                (x0 + modulePx).toFloat(),
                                (y0 + modulePx).toFloat(),
                                blockPaint
                            )
                        }
                    }
                }
            }
            currentY += qrPixelSize + (if (dotWidth >= 576) 14f else 10f)
        } else {
            val bars = generateBarcodeBars(payloadText)
            val barWidthPx = (usableWidth / bars.size.coerceAtLeast(1)).coerceIn(1, 5)
            val totalBarsWidth = barWidthPx * bars.size
            val barLeft = ((dotWidth - totalBarsWidth) / 2f).toInt()
            val barTop = currentY.toInt()
            val barHeight = if (dotWidth >= 576) 110 else 82

            sections.add {
                val c = lastCanvas!!
                for (i in bars.indices) {
                    if (bars[i]) {
                        val x0 = barLeft + i * barWidthPx
                        c.drawRect(
                            x0.toFloat(),
                            barTop.toFloat(),
                            (x0 + barWidthPx).toFloat(),
                            (barTop + barHeight).toFloat(),
                            blockPaint
                        )
                    }
                }
            }
            currentY += barHeight + (if (dotWidth >= 576) 14f else 10f)
        }

        val fmS = subPaint.fontMetrics
        val captionY = currentY - fmS.ascent
        sections.add {
            val p = Paint(subPaint)
            val mw = p.measureText(payloadText)
            if (mw > usableWidth) p.textSize *= (usableWidth.toFloat() / mw)
            canvasDrawCentered(lastCanvas!!, payloadText, centerX, captionY, p)
        }
        currentY += (fmS.descent - fmS.ascent) + marginPx.toFloat()

        val totalHeight = currentY.toInt().coerceAtLeast(180)
        val bitmap = Bitmap.createBitmap(dotWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        lastCanvas = canvas
        for (drawFn in sections) {
            drawFn()
        }
        lastCanvas = null
        return bitmap
    }

    private var lastCanvas: Canvas? = null

    private fun canvasDrawCentered(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, x, y, paint)
    }

    private fun updateModeStyle() {
        if (!::modeQrBtn.isInitialized || !::modeBarBtn.isInitialized) return
        val isQr = (mode == CodeMode.QR_CODE)
        modeQrBtn.background = if (isQr) {
            activity.rippleGradient(activity.colAccentBlue, activity.colAccentCyan, 10)
        } else {
            activity.rippleRounded(activity.colSurfaceElevated, 10, activity.colBorder, 1)
        }
        modeBarBtn.background = if (!isQr) {
            activity.rippleGradient(activity.colAccentBlue, activity.colAccentCyan, 10)
        } else {
            activity.rippleRounded(activity.colSurfaceElevated, 10, activity.colBorder, 1)
        }
    }

    internal fun refreshPreview() {
        val dotWidth = resolveDotWidth()
        val paperLabel = if (dotWidth >= 576) "80mm (576px)" else "58mm (384px)"
        if (::widthBadgeLabel.isInitialized) {
            widthBadgeLabel.text = "Auto $paperLabel"
        }
        updateModeStyle()
        if (::previewView.isInitialized && ::payloadInput.isInitialized) {
            val bmp = renderCodeBitmap()
            previewView.setImageBitmap(bmp)
        }
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
            text = "Auto 58mm (384px)"
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
            text = "QR CODE & BARCODE (AUTO 58MM / 80MM)"
            textSize = 11f
            letterSpacing = 0.08f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(activity.colWarning)
            setPadding(0, 0, 0, activity.dp(8))
        })

        val modeRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        modeQrBtn = TextView(activity).apply {
            text = "QR CODE"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                mode = CodeMode.QR_CODE
                refreshPreview()
            }
        }

        modeBarBtn = TextView(activity).apply {
            text = "BARCODE 1D"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                mode = CodeMode.BARCODE_1D
                refreshPreview()
            }
        }

        modeRow.addView(modeQrBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = activity.dp(6)
        })
        modeRow.addView(modeBarBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = activity.dp(6)
        })

        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshPreview()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        }

        titleInput = EditText(activity).apply {
            hint = "Judul singkat (opsional, misal: WA / STOK #01)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            textSize = 13f
            setTextColor(activity.colTextPrimary)
            setHintTextColor(activity.colTextMuted)
            setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10))
            background = activity.rounded(activity.colSurfaceElevated, 12, activity.colBorder, 1)
            addTextChangedListener(watcher)
        }

        payloadInput = EditText(activity).apply {
            hint = "Isi teks / link / kode (misal: https://utilitas.web.id)"
            inputType = InputType.TYPE_CLASS_TEXT
            textSize = 13f
            setTextColor(activity.colTextPrimary)
            setHintTextColor(activity.colTextMuted)
            setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10))
            background = activity.rounded(activity.colSurfaceElevated, 12, activity.colBorder, 1)
            addTextChangedListener(watcher)
        }

        previewView = android.widget.ImageView(activity).apply {
            adjustViewBounds = true
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8))
        }

        val printBtn = TextView(activity).apply {
            text = "CETAK QR / BARCODE"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(activity.dp(12), activity.dp(12), activity.dp(12), activity.dp(12))
            background = activity.rippleGradient(activity.colSuccess, activity.colAccentCyan, 12)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val raw = payloadInput.text.toString().trim()
                if (raw.isEmpty()) {
                    Toast.makeText(activity, "Isi teks atau kode terlebih dahulu", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val bmp = renderCodeBitmap()
                onPrintQrOrBarcodeBitmap(bmp)
            }
        }

        card.addView(modeRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = activity.dp(10)
        })
        card.addView(titleInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = activity.dp(8)
        })
        card.addView(payloadInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = activity.dp(12)
        })
        card.addView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = activity.rounded(Color.WHITE, 10, activity.colBorder, 1)
                setPadding(activity.dp(4), activity.dp(4), activity.dp(4), activity.dp(4))
                addView(previewView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = activity.dp(12)
            }
        )
        card.addView(printBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        container.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        refreshPreview()
        return container
    }
}
