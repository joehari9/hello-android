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

    private val rupiahLocale = Locale("in", "ID")
    private val currencyFormat = NumberFormat.getCurrencyInstance(rupiahLocale).apply {
        maximumFractionDigits = 0
    }

    private fun formatMoney(amount: Long): String {
        return currencyFormat.format(amount).replace("Rp", "Rp ").replace("\s+".toRegex(), " ")
    }

    // Menyesuaikan lebar cetak otomatis berdasarkan target/perangkat aktif tanpa pilihan manual 58/80mm
    private fun resolveTargetDotWidth(): Int {
        val targetViewWidth = if (activity.contentContainerWidth > 0) {
            activity.contentContainerWidth - activity.dp(36)
        } else {
            activity.resources.displayMetrics.widthPixels - activity.dp(36)
        }

        // Petakan lebar area tampilan ke rentang standar thermal (384 dot untuk sempit, 576 dot untuk lebar)
        val clampedDots = targetViewWidth.coerceIn(384, 576)
        return if (clampedDots < 480) 384 else 576
    }

    private fun resolveCharsPerLine(dotWidth: Int): Int {
        // 384 dot -> ~32 kolom (standar 58mm), 576 dot -> ~48 kolom (standar 80mm)
        return ((dotWidth * 32) / 384).coerceIn(32, 48)
    }

    private fun formatTwoColumns(left: String, right: String, width: Int): String {
        val cleanLeft = left.trim()
        val cleanRight = right.trim()
        if (cleanLeft.length + cleanRight.length + 1 <= width) {
            val spaces = width - cleanLeft.length - cleanRight.length
            return cleanLeft + " ".repeat(spaces) + cleanRight
        }
        val maxLeft = (width - cleanRight.length - 1).coerceAtLeast(1)
        val trimmedLeft = if (cleanLeft.length > maxLeft) {
            cleanLeft.take((maxLeft - 1).coerceAtLeast(1)) + "."
        } else {
            cleanLeft
        }
        val spaces = (width - trimmedLeft.length - cleanRight.length).coerceAtLeast(1)
        return trimmedLeft + " ".repeat(spaces) + cleanRight
    }

    private fun centerText(text: String, width: Int): String {
        val clean = text.trim()
        if (clean.length >= width) return clean.take(width)
        val pad = (width - clean.length) / 2
        return " ".repeat(pad) + clean
    }

    private fun buildReceiptLines(): List<String> {
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
        val paidRaw = paidInput.text.toString().replace("[^0-9]".toRegex(), "").toLongOrNull() ?: 0L
        val paid = if (paidRaw == 0L && total > 0L) total else paidRaw
        val change = (paid - total).coerceAtLeast(0L)

        val lines = mutableListOf<String>()
        lines.add(centerText(storeName.uppercase(Locale.getDefault()), cols))
        if (storeAddress.isNotEmpty()) {
            lines.add(centerText(storeAddress, cols))
        }
        if (storePhone.isNotEmpty()) {
            lines.add(centerText("Telp: $storePhone", cols))
        }
        lines.add(doubleDivider)
        lines.add(formatTwoColumns("Tgl: $nowStr", "Auto ${dotWidth}px", cols))
        lines.add(divider)

        if (items.isEmpty()) {
            lines.add(centerText("(Belum ada item ditambahkan)", cols))
        } else {
            items.forEachIndexed { idx, item ->
                val itemHeader = "${idx + 1}. ${item.name}"
                lines.add(itemHeader.take(cols))
                val qtyPrice = "  ${item.qty} x ${formatMoney(item.price)}"
                lines.add(formatTwoColumns(qtyPrice, formatMoney(item.subtotal), cols))
            }
        }

        lines.add(divider)
        lines.add(formatTwoColumns("TOTAL", formatMoney(total), cols))
        lines.add(formatTwoColumns("BAYAR", formatMoney(paid), cols))
        lines.add(formatTwoColumns("KEMBALI", formatMoney(change), cols))
        lines.add(doubleDivider)
        lines.add(centerText(footerNote, cols))
        lines.add("")
        return lines
    }

    private fun refreshAll() {
        val dotWidth = resolveTargetDotWidth()
        val cols = resolveCharsPerLine(dotWidth)
        widthInfoLabel.text = "Lebar Otomatis: ${dotWidth}px ($cols kolom)"

        val total = items.sumOf { it.subtotal }
        val paidRaw = paidInput.text.toString().replace("[^0-9]".toRegex(), "").toLongOrNull() ?: 0L
        val paid = if (paidRaw == 0L && total > 0L) total else paidRaw
        val change = (paid - total).coerceAtLeast(0L)

        summaryText.text = "Item: ${items.size}  •  Total: ${formatMoney(total)}  •  Kembali: ${formatMoney(change)}"
        livePreviewText.text = buildReceiptLines().joinToString("\n")

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
        val lines = buildReceiptLines()
        val dotWidth = resolveTargetDotWidth()
        val cols = resolveCharsPerLine(dotWidth)

        val horizontalPadding = 16
        val verticalPadding = 20
        val usableWidth = dotWidth - (horizontalPadding * 2)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.MONOSPACE
            textSize = 22f
        }

        // Sesuaikan ukuran font monospace agar muat pas persis dalam `cols` karakter di `usableWidth`
        val sampleLine = "M".repeat(cols)
        val measured = paint.measureText(sampleLine)
        if (measured > 0f) {
            paint.textSize = paint.textSize * (usableWidth.toFloat() / measured)
        }

        val fm = paint.fontMetrics
        val lineHeight = ((fm.descent - fm.ascent) * 1.15f).toInt().coerceAtLeast(24)
        val bitmapHeight = (lines.size * lineHeight) + (verticalPadding * 2)

        val bitmap = Bitmap.createBitmap(dotWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var y = verticalPadding - fm.ascent
        for (line in lines) {
            canvas.drawText(line, horizontalPadding.toFloat(), y, paint)
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
            setTextColor(activity.colTextPrimary)
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

        livePreviewText = TextView(activity).apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
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
                addView(livePreviewText, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = activity.dp(12)
            }
        )
        previewCard.addView(actionRow)

        container.addView(previewCard)

        refreshAll()
        return container
    }
}
