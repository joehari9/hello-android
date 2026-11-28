package com.example.helloandroid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ManualReceiptView(
    private val activity: MainActivity,
    private val onBackToHome: () -> Unit,
    private val onPrintBitmap: (Bitmap) -> Unit
) {
    private data class ReceiptItem(
        val id: Int,
        val name: String,
        val qty: Int,
        val price: Long
    ) {
        val subtotal: Long get() = qty.toLong() * price
    }

    private val items = mutableListOf<ReceiptItem>()
    private var nextItemId = 1
    private val receiptPrefs by lazy {
        activity.getSharedPreferences("manual_receipt_header_footer", android.content.Context.MODE_PRIVATE)
    }

    private fun loadHeaderFooterPrefs() {
        storeNameInput.setText(receiptPrefs.getString("store_name", "").orEmpty())
        storeAddressInput.setText(receiptPrefs.getString("store_address", "").orEmpty())
        storePhoneInput.setText(receiptPrefs.getString("store_phone", "").orEmpty())
        footerNoteInput.setText(receiptPrefs.getString("footer_note", "").orEmpty())
    }

    private fun saveHeaderFooterPrefs() {
        receiptPrefs.edit()
            .putString("store_name", storeNameInput.text.toString().trim())
            .putString("store_address", storeAddressInput.text.toString().trim())
            .putString("store_phone", storePhoneInput.text.toString().trim())
            .putString("footer_note", footerNoteInput.text.toString().trim())
            .apply()
    }

    private lateinit var storeNameInput: EditText
    private lateinit var storeAddressInput: EditText
    private lateinit var storePhoneInput: EditText
    private lateinit var itemNameInput: EditText
    private lateinit var itemQtyInput: EditText
    private lateinit var itemPriceInput: EditText
    private lateinit var paidInput: EditText
    private lateinit var footerNoteInput: EditText

    private lateinit var itemListContainer: LinearLayout
    private lateinit var summaryText: TextView
    private lateinit var livePreviewText: TextView
    private lateinit var widthInfoLabel: TextView

    private fun formatMoney(amount: Long): String {
        val sign = if (amount < 0L) "-" else ""
        val absVal = kotlin.math.abs(amount)
        val grouped = absVal.toString().reversed().chunked(3).joinToString(".").reversed()
        return "${sign}Rp $grouped"
    }

    // Menyesuaikan lebar cetak otomatis berdasarkan perangkat/area aktif tanpa tombol pilih manual 58/80mm
    private fun resolveTargetDotWidth(): Int {
        val connectedName = activity.getConnectedPrinterNameForAutoWidth().lowercase(Locale.getDefault())
        if (connectedName.contains("80") || connectedName.contains("576") || connectedName.contains("72mm")) {
            return 576
        }
        return 384
    }

    private fun resolveCharsPerLine(dotWidth: Int): Int {
        // 384 dot -> ~32 kolom (standar 58mm), 576 dot -> ~48 kolom (standar 80mm)
        return ((dotWidth * 32) / 384).coerceIn(32, 48)
    }

    private data class ReceiptLine(
        val left: String,
        val right: String = "",
        val align: Paint.Align = Paint.Align.LEFT,
        val bold: Boolean = false
    )

    private fun wrapTextToLines(text: String, maxChars: Int): List<String> {
        val clean = text.trim()
        if (clean.isEmpty()) return emptyList()
        if (clean.length <= maxChars) return listOf(clean)

        val words = clean.split("\\s+".toRegex())
        val out = mutableListOf<String>()
        var current = ""

        for (w in words) {
            if (w.length > maxChars) {
                if (current.isNotEmpty()) {
                    out.add(current)
                    current = ""
                }
                var start = 0
                while (start < w.length) {
                    val end = minOf(start + maxChars, w.length)
                    out.add(w.substring(start, end))
                    start = end
                }
                continue
            }
            val candidate = if (current.isEmpty()) w else "$current $w"
            if (candidate.length <= maxChars) {
                current = candidate
            } else {
                out.add(current)
                current = w
            }
        }
        if (current.isNotEmpty()) out.add(current)
        return out
    }

    private fun fitLeftRightText(left: String, right: String, maxChars: Int): String {
        val l = left.trim()
        val r = right.trim()
        if (r.isEmpty()) return l.take(maxChars)
        if (l.length + r.length + 1 <= maxChars) {
            return l + " ".repeat(maxChars - l.length - r.length) + r
        }
        val availLeft = (maxChars - r.length - 1).coerceAtLeast(1)
        val trimmedL = if (l.length > availLeft) l.take((availLeft - 1).coerceAtLeast(1)) + "." else l
        val gap = (maxChars - trimmedL.length - r.length).coerceAtLeast(1)
        return trimmedL + " ".repeat(gap) + r
    }

    private fun fitCenterText(text: String, maxChars: Int): String {
        val clean = text.trim()
        if (clean.length >= maxChars) return clean.take(maxChars)
        val pad = (maxChars - clean.length) / 2
        return " ".repeat(pad) + clean
    }

    private fun buildReceiptStructuredLines(): List<ReceiptLine> {
        val dotWidth = resolveTargetDotWidth()
        val cols = resolveCharsPerLine(dotWidth)
        val divider = "-".repeat(cols)
        val doubleDivider = "=".repeat(cols)

        val storeName = storeNameInput.text.toString().trim().ifEmpty { "TOKO JZ" }
        val storeAddress = storeAddressInput.text.toString().trim()
        val storePhone = storePhoneInput.text.toString().trim()
        val footerNote = footerNoteInput.text.toString().trim().ifEmpty { "Terima kasih atas kunjungan Anda" }

        val nowStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val total = items.sumOf { it.subtotal }
        val paidDigits = paidInput.text.toString().replace("[^0-9]".toRegex(), "")
        val paidIsFilled = paidDigits.isNotEmpty()
        val paid = if (paidIsFilled) (paidDigits.toLongOrNull() ?: 0L) else 0L
        val change = if (paidIsFilled && paid >= total) (paid - total) else 0L

        val out = mutableListOf<ReceiptLine>()

        // Header selalu di tengah (CENTER) dan di-wrap agar tidak terpotong
        for (line in wrapTextToLines(storeName.uppercase(Locale.getDefault()), cols)) {
            out.add(ReceiptLine(left = line, align = Paint.Align.CENTER, bold = true))
        }
        if (storeAddress.isNotEmpty()) {
            for (line in wrapTextToLines(storeAddress, cols)) {
                out.add(ReceiptLine(left = line, align = Paint.Align.CENTER))
            }
        }
        if (storePhone.isNotEmpty()) {
            for (line in wrapTextToLines("Telp: $storePhone", cols)) {
                out.add(ReceiptLine(left = line, align = Paint.Align.CENTER))
            }
        }

        out.add(ReceiptLine(left = doubleDivider, align = Paint.Align.CENTER))
        out.add(ReceiptLine(left = "Tgl: $nowStr", right = "Auto", align = Paint.Align.LEFT))
        out.add(ReceiptLine(left = divider, align = Paint.Align.CENTER))

        if (items.isEmpty()) {
            out.add(ReceiptLine(left = "(Belum ada item ditambahkan)", align = Paint.Align.CENTER))
        } else {
            items.forEachIndexed { idx, item ->
                val titleLines = wrapTextToLines("${idx + 1}. ${item.name}", cols)
                titleLines.forEachIndexed { lineIdx, tLine ->
                    out.add(ReceiptLine(left = if (lineIdx == 0) tLine else "  $tLine", align = Paint.Align.LEFT, bold = lineIdx == 0))
                }
                out.add(
                    ReceiptLine(
                        left = "  ${item.qty} x ${formatMoney(item.price)}",
                        right = formatMoney(item.subtotal),
                        align = Paint.Align.LEFT
                    )
                )
            }
        }

        out.add(ReceiptLine(left = divider, align = Paint.Align.CENTER))
        out.add(ReceiptLine(left = "TOTAL", right = formatMoney(total), align = Paint.Align.LEFT, bold = true))
        out.add(ReceiptLine(left = "BAYAR", right = formatMoney(paid), align = Paint.Align.LEFT))
        out.add(ReceiptLine(left = "KEMBALI", right = formatMoney(change), align = Paint.Align.LEFT))
        out.add(ReceiptLine(left = doubleDivider, align = Paint.Align.CENTER))

        for (line in wrapTextToLines(footerNote, cols)) {
            out.add(ReceiptLine(left = line, align = Paint.Align.CENTER))
        }
        out.add(ReceiptLine(left = "", align = Paint.Align.LEFT))
        return out
    }

private fun buildPreviewString(): CharSequence {
    val structured = buildReceiptStructuredLines()
    val cols = resolveCharsPerLine(resolveTargetDotWidth()).coerceAtLeast(16)
    val sb = android.text.SpannableStringBuilder()

    for ((idx, rl) in structured.withIndex()) {
        val lineText: String = when {
            rl.right.isNotEmpty() -> {
                val leftClean = rl.left.trim()
                val rightClean = rl.right.trim()
                val spaceCount = (cols - leftClean.length - rightClean.length).coerceAtLeast(1)
                leftClean + " ".repeat(spaceCount) + rightClean
            }
            rl.align == Paint.Align.CENTER -> rl.left.trim()
            else -> rl.left
        }

        val start = sb.length
        sb.append(lineText)
        val end = sb.length

        if (rl.align == Paint.Align.CENTER && rl.right.isEmpty()) {
            sb.setSpan(
                android.text.style.AlignmentSpan.Standard(android.text.Layout.Alignment.ALIGN_CENTER),
                start,
                end,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        if (idx < structured.lastIndex) {
            sb.append("\n")
        }
    }

    return sb
}


    private fun refreshAll() {
        val dotWidth = resolveTargetDotWidth()
        val cols = resolveCharsPerLine(dotWidth)
        widthInfoLabel.text = "Lebar Otomatis: ${dotWidth}px ($cols kolom)"

        val total = items.sumOf { it.subtotal }
        val paidDigits = paidInput.text.toString().replace("[^0-9]".toRegex(), "")
        val paidIsFilled = paidDigits.isNotEmpty()
        val paid = if (paidIsFilled) (paidDigits.toLongOrNull() ?: 0L) else 0L
        val change = if (paidIsFilled && paid >= total) (paid - total) else 0L

        summaryText.text = "Item: ${items.size}  •  Total: ${formatMoney(total)}  •  Kembali: ${formatMoney(change)}"
        livePreviewText.text = buildPreviewString()

        itemListContainer.removeAllViews()
        items.forEach { item ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8))
                background = activity.rounded(activity.colSurfaceElevated, 12, activity.colBorder, 1)
            }

            val infoCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            infoCol.addView(TextView(activity).apply {
                text = item.name
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(activity.colTextPrimary)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })

            infoCol.addView(TextView(activity).apply {
                text = "${item.qty} x ${formatMoney(item.price)} = ${formatMoney(item.subtotal)}"
                textSize = 11.5f
                setTextColor(activity.colTextSecondary)
                setPadding(0, activity.dp(2), 0, 0)
            })

            val removeBtn = TextView(activity).apply {
                text = "✕"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(activity.colWarning)
                background = activity.rippleRounded(activity.colBgPrimary, 10, activity.colBorder, 1)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    items.removeAll { it.id == item.id }
                    refreshAll()
                }
            }

            row.addView(infoCol)
            row.addView(removeBtn, LinearLayout.LayoutParams(activity.dp(32), activity.dp(32)).apply {
                leftMargin = activity.dp(8)
            })

            itemListContainer.addView(
                row,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = activity.dp(8)
                }
            )
        }
    }

    private fun renderReceiptBitmap(): Bitmap {
        val structured = buildReceiptStructuredLines()
        val dotWidth = resolveTargetDotWidth()
        val cols = resolveCharsPerLine(dotWidth)

        // Margin aman ekstra agar tidak terpotong mekanisme roll 58mm
        val horizontalPadding = 24
        val verticalPadding = 20
        val usableWidth = (dotWidth - (horizontalPadding * 2)).coerceAtLeast(120)

        val regularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.MONOSPACE
            textSize = 22f
        }

        // Kalibrasi ukuran font berdasarkan lebar karakter terlebar (W/M) agar tidak pernah melewati usableWidth
        val widestSample = "W".repeat(cols)
        val measured = regularPaint.measureText(widestSample)
        if (measured > 0f) {
            regularPaint.textSize = (regularPaint.textSize * (usableWidth.toFloat() / measured)) * 0.96f
        }

        val boldPaint = Paint(regularPaint).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        val fm = regularPaint.fontMetrics
        val lineHeight = ((fm.descent - fm.ascent) * 1.18f).toInt().coerceAtLeast(24)
        val bitmapHeight = (structured.size * lineHeight) + (verticalPadding * 2)

        val bitmap = Bitmap.createBitmap(dotWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val leftEdge = horizontalPadding.toFloat()
        val rightEdge = (dotWidth - horizontalPadding).toFloat()
        val centerX = dotWidth / 2f

        var y = verticalPadding - fm.ascent
        for (rl in structured) {
            val p = Paint(if (rl.bold) boldPaint else regularPaint)
            when {
                rl.right.isNotEmpty() -> {
                    p.textAlign = Paint.Align.LEFT
                    val maxLeftW = (rightEdge - leftEdge - p.measureText(rl.right) - p.measureText("  ")).coerceAtLeast(10f)
                    var lText = rl.left.trim()
                    while (lText.length > 1 && p.measureText(lText) > maxLeftW) {
                        lText = lText.dropLast(1)
                    }
                    canvas.drawText(lText, leftEdge, y, p)

                    p.textAlign = Paint.Align.RIGHT
                    canvas.drawText(rl.right.trim(), rightEdge, y, p)
                }
                rl.align == Paint.Align.CENTER -> {
                    val centeredText = rl.left.trim()
                    val mw = p.measureText(centeredText)
                    if (mw > usableWidth) {
                        p.textSize = p.textSize * (usableWidth.toFloat() / mw)
                    }
                    p.textAlign = Paint.Align.CENTER
                    canvas.drawText(centeredText, centerX, y, p)
                }
                else -> {
                    p.textAlign = Paint.Align.LEFT
                    canvas.drawText(rl.left, leftEdge, y, p)
                }
            }
            y += lineHeight
        }

        return bitmap
    }


    private fun styledInput(hint: String, inputType: Int): EditText {
        return EditText(activity).apply {
            this.hint = hint
            this.inputType = inputType
            textSize = 13f
            setTextColor(activity.colTextPrimary)
            setHintTextColor(activity.colTextMuted)
            setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10))
            background = activity.rounded(activity.colSurfaceElevated, 12, activity.colBorder, 1)
        }
    }

    private fun sectionCard(): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(14), activity.dp(14), activity.dp(14), activity.dp(14))
            background = activity.rounded(activity.colSurface, 18, activity.colBorder, 1)
            elevation = activity.dp(2).toFloat()
        }
    }

    fun buildView(): View {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(18), activity.dp(8), activity.dp(18), activity.dp(28))
            setBackgroundColor(activity.colBgPrimary)
        }

        // Top Bar Sub-Halaman Manual Struk
        val subHeader = LinearLayout(activity).apply {
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

        widthInfoLabel = TextView(activity).apply {
            text = "Lebar Otomatis"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(activity.colAccentCyan)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        subHeader.addView(backBtn)
        subHeader.addView(widthInfoLabel)
        container.addView(subHeader)

        // 1. Kartu Header Struk
        val headerCard = sectionCard()
        headerCard.addView(TextView(activity).apply {
            text = "HEADER & IDENTITAS"
            textSize = 11f
            letterSpacing = 0.08f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(activity.colAccentCyan)
            setPadding(0, 0, 0, activity.dp(8))
        })

        storeNameInput = styledInput("Nama Toko / Usaha (wajib)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        storeAddressInput = styledInput("Alamat singkat (opsional)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        storePhoneInput = styledInput("No. HP / WA (opsional)", InputType.TYPE_CLASS_PHONE)

        val refreshTextWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (::livePreviewText.isInitialized) {
                    refreshAll()
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        }

        storeNameInput.addTextChangedListener(refreshTextWatcher)
        storeAddressInput.addTextChangedListener(refreshTextWatcher)
        storePhoneInput.addTextChangedListener(refreshTextWatcher)

        headerCard.addView(storeNameInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = activity.dp(8)
        })
        headerCard.addView(storeAddressInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = activity.dp(8)
        })
        headerCard.addView(storePhoneInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        container.addView(headerCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = activity.dp(12)
        })

        // 2. Kartu Input Item Transaksi
        val itemCard = sectionCard()
        itemCard.addView(TextView(activity).apply {
            text = "TAMBAH ITEM / JASA"
            textSize = 11f
            letterSpacing = 0.08f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(activity.colSuccess)
            setPadding(0, 0, 0, activity.dp(8))
        })

        itemNameInput = styledInput("Nama barang / jasa", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        itemQtyInput = styledInput("Qty", InputType.TYPE_CLASS_NUMBER).apply {
            setText("1")
        }
        itemPriceInput = styledInput("Harga satuan", InputType.TYPE_CLASS_NUMBER)

        val qtyPriceRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        qtyPriceRow.addView(itemQtyInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.35f).apply {
            rightMargin = activity.dp(6)
        })
        qtyPriceRow.addView(itemPriceInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.65f).apply {
            leftMargin = activity.dp(6)
        })

        val addItemBtn = TextView(activity).apply {
            text = "+ TAMBAH KE STRUK"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(activity.dp(12), activity.dp(11), activity.dp(12), activity.dp(11))
            background = activity.rippleGradient(activity.colAccentBlue, activity.colAccentCyan, 12)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val name = itemNameInput.text.toString().trim()
                val qty = itemQtyInput.text.toString().trim().toIntOrNull() ?: 0
                val price = itemPriceInput.text.toString().replace("[^0-9]".toRegex(), "").toLongOrNull() ?: -1L

                if (name.isEmpty()) {
                    Toast.makeText(activity, "Nama item belum diisi", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (qty <= 0) {
                    Toast.makeText(activity, "Qty minimal 1", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (price < 0L) {
                    Toast.makeText(activity, "Harga item belum valid", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                items.add(ReceiptItem(nextItemId++, name, qty, price))
                itemNameInput.setText("")
                itemQtyInput.setText("1")
                itemPriceInput.setText("")
                refreshAll()
            }
        }

        itemListContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        itemCard.addView(itemNameInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = activity.dp(8)
        })
        itemCard.addView(qtyPriceRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = activity.dp(10)
        })
        itemCard.addView(addItemBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = activity.dp(12)
        })
        itemCard.addView(itemListContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        container.addView(itemCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = activity.dp(12)
        })

        // 3. Kartu Pembayaran & Footer
        val paymentCard = sectionCard()
        paymentCard.addView(TextView(activity).apply {
            text = "PEMBAYARAN & FOOTER"
            textSize = 11f
            letterSpacing = 0.08f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(activity.colWarning)
            setPadding(0, 0, 0, activity.dp(8))
        })

        paidInput = styledInput("Uang diterima (kosongkan = pas)", InputType.TYPE_CLASS_NUMBER).apply {
            addTextChangedListener(refreshTextWatcher)
        }
        footerNoteInput = styledInput("Catatan bawah / ucapan (opsional)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).apply {
            addTextChangedListener(refreshTextWatcher)
        }

        summaryText = TextView(activity).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(activity.colTextPrimary)
            setPadding(0, activity.dp(4), 0, 0)
        }

        paymentCard.addView(paidInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = activity.dp(8)
        })
        paymentCard.addView(footerNoteInput, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = activity.dp(6)
        })
        paymentCard.addView(summaryText)

        container.addView(paymentCard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = activity.dp(12)
        })

        // 4. Preview Struk Otomatis & Tombol Cetak
        val previewCard = sectionCard()
        previewCard.addView(TextView(activity).apply {
            text = "PREVIEW STRUK OTOMATIS"
            textSize = 11f
            letterSpacing = 0.08f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(activity.colAccentCyan)
            setPadding(0, 0, 0, activity.dp(8))
        })

          livePreviewText = object : TextView(activity) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                if (w > 0) {
                    val cols = resolveCharsPerLine(resolveTargetDotWidth())
                    val usable = (w - paddingLeft - paddingRight).coerceAtLeast(80)
                    val sample = "W".repeat(cols)
                    val probe = Paint(paint)
                    probe.textSize = 14f
                    val mw = probe.measureText(sample)
                    if (mw > 0f) {
                        val fittedPx = (14f * (usable.toFloat() / mw)) * 0.95f
                        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, fittedPx.coerceIn(10f, 36f))
                    }
                }
            }
        }.apply {
            textSize = 10f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER_HORIZONTAL
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE)
            setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10))
        }


        val actionRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val clearBtn = TextView(activity).apply {
            text = "RESET"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(activity.colTextPrimary)
            setPadding(activity.dp(10), activity.dp(12), activity.dp(10), activity.dp(12))
            background = activity.rippleRounded(activity.colSurfaceElevated, 12, activity.colBorder, 1)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                items.clear()
                paidInput.setText("")
                itemNameInput.setText("")
                itemPriceInput.setText("")
                refreshAll()
            }
        }

        val printBtn = TextView(activity).apply {
            text = "CETAK STRUK"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(activity.dp(12), activity.dp(12), activity.dp(12), activity.dp(12))
            background = activity.rippleGradient(activity.colSuccess, activity.colAccentCyan, 12)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (items.isEmpty()) {
                    Toast.makeText(activity, "Tambahkan minimal 1 item dulu", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                saveHeaderFooterPrefs()
                val bmp = renderReceiptBitmap()
                onPrintBitmap(bmp)
            }
        }

        actionRow.addView(clearBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.35f).apply {
            rightMargin = activity.dp(6)
        })
        actionRow.addView(printBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.65f).apply {
            leftMargin = activity.dp(6)
        })

 previewCard.addView(
    FrameLayout(activity).apply {
        background = activity.rounded(Color.WHITE, 10, activity.colBorder, 1)
        clipToOutline = true
        addView(
            livePreviewText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
    },
    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = activity.dp(12)
    }
)

        previewCard.addView(actionRow)

        container.addView(previewCard)
        loadHeaderFooterPrefs()
        refreshAll()
        return container
    }
}
