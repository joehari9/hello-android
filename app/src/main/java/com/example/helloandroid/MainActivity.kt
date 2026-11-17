
package com.example.helloandroid

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.UUID
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.widget.ImageView
import android.widget.ScrollView
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable

class MainActivity : Activity() {

    private val printerName = "JZ-MP250"

    private val sppUUID = UUID.fromString(
        "00001101-0000-1000-8000-00805F9B34FB"
    )

    private var adapter: BluetoothAdapter? = null
    private var printer: BluetoothDevice? = null
    private var socket: BluetoothSocket? = null
    private lateinit var pdfPreview: ImageView
    private var processedBitmap: Bitmap? = null
    private lateinit var status: TextView
    private lateinit var deviceInfo: TextView
    private lateinit var connectButton: Button
    private lateinit var printButton: Button

    companion object {
        private const val REQUEST_BT = 100
        private const val REQUEST_PDF = 200
        private const val APP_WATERMARK =
        "Generate by: JZ Receipt Printer"
    }

  // onCreate start

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val manager = getSystemService(BluetoothManager::class.java)
    adapter = manager.adapter

    // Warna tema
    val navy = Color.rgb(16, 28, 45)
    val cardColor = Color.rgb(27, 44, 65)
    val blue = Color.rgb(35, 105, 170)
    val green = Color.rgb(22, 134, 83)
    val white = Color.WHITE
    val muted = Color.rgb(165, 185, 205)

    // Background rounded
    fun rounded(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
    }

    // resize to dp
    fun dp(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt()
    }
    

    // Layout utama
    val layout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(22, 24, 22, 24)
        setBackgroundColor(navy)
    }

    // Helper jarak
    fun space(height: Int) {
        layout.addView(
            TextView(this),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                height
            )
        )
    }

    // Header
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    val logo = TextView(this).apply {
        text = "▣"
        textSize = 30f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(100, 185, 255))
        background = rounded(cardColor, 18f)
        setPadding(12, 8, 12, 8)
    }

    val headerText = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(12, 0, 0, 0)
    }

    val title = TextView(this).apply {
        text = "JZ RECEIPT"
        textSize = 23f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(white)
    }

    val subtitle = TextView(this).apply {
        text = "THERMAL PRINT STUDIO"
        textSize = 11f
        setTextColor(muted)
    }

    headerText.addView(title)
    headerText.addView(subtitle)

    header.addView(logo)
    header.addView(headerText)

    layout.addView(header)

    space(25)

    // Judul koneksi
    val connectionTitle = TextView(this).apply {
        text = "PRINTER CONNECTION"
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(white)
    }

    layout.addView(connectionTitle)
    space(10)

    // Kartu koneksi
    val connectionPanel = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(18, 18, 18, 18)
        background = rounded(cardColor, 22f)
    }

    deviceInfo = TextView(this).apply {
        text = "Printer: Belum ditemukan"
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(white)
    }

    status = TextView(this).apply {
        text = "Status: Belum terhubung"
        textSize = 13f
        setTextColor(muted)
        setPadding(0, 8, 0, 12)
    }

    connectionPanel.addView(deviceInfo)
    connectionPanel.addView(status)

// Tombol pencarian
val searchButton = TextView(this).apply {
    text = "SCAN DEVICE"
    textSize = 14f
    typeface = Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER
    setTextColor(Color.WHITE)

    background = rounded(blue, 14f)

    isClickable = true
    isFocusable = true

    setPadding(dp(12), dp(4), dp(12), dp(4))
    minHeight = dp(56)
    includeFontPadding = false

    setOnClickListener {
        findPrinter()
    }
}




// Tombol koneksi
connectButton = Button(this).apply {
    text = "CONNECT PRINTER"
    isAllCaps = false
    textSize = 14f
    typeface = Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER

    setTextColor(Color.WHITE)
    background = rounded(Color.rgb(48, 79, 110), 14f)
    backgroundTintList = null

    minHeight = dp(56)
    setPadding(dp(12), dp(4), dp(12), dp(4))

    setOnClickListener {
        connectPrinter()
    }
}

connectionPanel.addView(
    searchButton,
    LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        dp(56)
    )
)

    val connectParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        dp(56)
    ).apply {
        topMargin = dp(8)
    }

    connectionPanel.addView(connectButton, connectParams)

    layout.addView(
        connectionPanel,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    )

    space(24)

    // Judul preview
    val previewTitle = TextView(this).apply {
        text = "RECEIPT PREVIEW"
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(white)
    }

    layout.addView(previewTitle)
    space(10)

    // Panel kertas putih
    val previewPanel = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(12, 12, 12, 12)
        background = rounded(white, 16f)
        minimumHeight = 280
    }

    pdfPreview = ImageView(this).apply {
        adjustViewBounds = true
        scaleType = ImageView.ScaleType.FIT_CENTER
        minimumHeight = 250
    }

    previewPanel.addView(
        pdfPreview,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    )

    layout.addView(
        previewPanel,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    )

    space(18)

    // Tombol pilih PDF
    val selectPdfButton = Button(this).apply {
        text = "SELECT RECEIPT PDF"
        isAllCaps = false
        setTextColor(white)
        background = rounded(blue, 16f)
        
        gravity = Gravity.CENTER
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        backgroundTintList = null
        

        setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
            }

            startActivityForResult(intent, REQUEST_PDF)
        }
    }

    layout.addView(
        selectPdfButton,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            56
        )
    )

    // Tombol cetak
    printButton = Button(this).apply {
        text = "PRINT RECEIPT"
        isAllCaps = false
        isEnabled = false
        setTextColor(white)
        background = rounded(green, 16f)
        
        gravity = Gravity.CENTER
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        backgroundTintList = null
        

        setOnClickListener {
            printReceipt()
        }
    }

    val printParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        58
    ).apply {
        topMargin = 10
    }

    layout.addView(printButton, printParams)

    space(22)

    // Footer
    val footer = TextView(this).apply {
        text = "JZ RECEIPT PRINTER  •  BUILT FOR THERMAL PRINTING"
        textSize = 10f
        gravity = Gravity.CENTER
        setTextColor(muted)
    }

    layout.addView(footer)

    // Scroll layar
    val scrollView = ScrollView(this).apply {
        setFillViewport(true)
        setBackgroundColor(navy)
        addView(layout)
    }

    setContentView(scrollView)
}

  // onCreate end

    private fun hasBluetoothPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true

        return checkSelfPermission(
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31 && !hasBluetoothPermission()) {
            requestPermissions(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_BT
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
        )

        if (requestCode == REQUEST_BT &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            findPrinter()
        }
    }

    private fun findPrinter() {
        if (!hasBluetoothPermission()) {
            requestBluetoothPermission()
            return
        }

        try {
            val bt = adapter

            if (bt == null || !bt.isEnabled) {
                status.text = "Bluetooth belum aktif"
                return
            }

            val devices = bt.bondedDevices

            printer = devices.firstOrNull {
                it.name.equals(printerName, ignoreCase = true)
            }

            if (printer != null) {
                deviceInfo.text = "Printer ditemukan: ${printer?.name}"
                status.text = "Siap dihubungkan"
            } else {
                deviceInfo.text = "Printer tidak ditemukan"

                val names = devices.joinToString("\n") {
                    it.name ?: "Unknown"
                }

                status.text = "Perangkat tersimpan:\n$names"
            }

        } catch (e: Exception) {
            status.text = "Error: ${e.message}"
        }
    }

    private fun connectPrinter() {
        if (!hasBluetoothPermission()) {
            requestBluetoothPermission()
            return
        }

        if (printer == null) {
            findPrinter()
            return
        }

        status.text = "Menghubungkan..."

        Thread {
            try {
                socket?.close()

                val newSocket =
                    printer!!.createRfcommSocketToServiceRecord(sppUUID)

                newSocket.connect()
                socket = newSocket

                runOnUiThread {
                    status.text = "Bluetooth TERHUBUNG!"
                    printButton.isEnabled = true
                }

            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Gagal koneksi: ${e.message}"
                    printButton.isEnabled = false
                }
            }
        }.start()
    }

    private fun testPrint() {
        val activeSocket = socket

        if (activeSocket == null || !activeSocket.isConnected) {
            status.text = "Printer belum terhubung"
            return
        }

        status.text = "Mengirim data..."

        Thread {
            try {
                val output = activeSocket.outputStream

                output.write(
                    byteArrayOf(
                        0x1B.toByte(), 0x40.toByte(),
                        0x1B.toByte(), 0x61.toByte(), 0x01.toByte()
                    )
                )

                output.write(
                    "HELLO BLUETOOTH\nJZ-MP250\n\n".toByteArray(Charsets.US_ASCII)
                )

                output.write(
                    byteArrayOf(
                        0x1B.toByte(), 0x64.toByte(), 0x04.toByte()
                    )
                )

                output.flush()

                runOnUiThread {
                    status.text = "Test print berhasil dikirim!"
                }

            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Gagal mencetak: ${e.message}"
                }
            }
        }.start()
    }

private fun printReceipt() {

    val bitmap = processedBitmap

    if (bitmap == null) {
        status.text = "Pilih PDF resi terlebih dahulu"
        return
    }
    val printBitmap = addWatermark(
    bitmap,
    APP_WATERMARK
)
    val activeSocket = socket

    if (activeSocket == null || !activeSocket.isConnected) {
        status.text = "Printer belum terhubung"
        return
    }

    status.text = "Sedang mencetak resi..."

    Thread {
        try {
            val output = activeSocket.outputStream

            // Inisialisasi printer dan posisi tengah
            output.write(
                byteArrayOf(
                    0x1B, 0x40,
                    0x1B, 0x61, 0x01
                )
            )

            val width = printBitmap.width
            val height = printBitmap.height
            val widthBytes = width / 8

            // Kirim gambar per 16 baris
            var y = 0

            while (y < height) {

                val chunkHeight = minOf(16, height - y)

                // Perintah ESC/POS GS v 0
                output.write(
                    byteArrayOf(
                        0x1D, 0x76, 0x30, 0x00,
                        (widthBytes and 0xFF).toByte(),
                        ((widthBytes shr 8) and 0xFF).toByte(),
                        (chunkHeight and 0xFF).toByte(),
                        ((chunkHeight shr 8) and 0xFF).toByte()
                    )
                )

                val imageData = ByteArray(widthBytes * chunkHeight)

                for (row in 0 until chunkHeight) {
                    for (x in 0 until width) {

                        val pixel = printBitmap.getPixel(x, y + row)

                        val r = android.graphics.Color.red(pixel)
                        val g = android.graphics.Color.green(pixel)
                        val b = android.graphics.Color.blue(pixel)

                        val gray = (
                            0.299 * r +
                            0.587 * g +
                            0.114 * b
                        ).toInt()

                        if (gray < 128) {

                            val byteIndex =
                                row * widthBytes + x / 8

                            val bit = 7 - (x % 8)

                            imageData[byteIndex] =
                                (imageData[byteIndex].toInt() or
                                (1 shl bit)).toByte()
                        }
                    }
                }

                output.write(imageData)
                output.flush()

                Thread.sleep(10)

                y += chunkHeight
            }

            // Feed dan potong kertas
            output.write(
                byteArrayOf(
                    0x1B, 0x64, 0x04,
                    0x1D, 0x56, 0x41, 0x03
                )
            )

            output.flush()

            runOnUiThread {
                status.text = "Resi berhasil dikirim ke printer!"
            }

        } catch (e: Exception) {
            runOnUiThread {
                status.text = "Gagal mencetak: ${e.message}"
            }
        }

    }.start()
}

    
    override fun onDestroy() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }

        super.onDestroy()
    }
    // Fungsi untuk menerima file PDF yang dipilih
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_PDF &&
            resultCode == RESULT_OK
        ) {
            val uri = data?.data ?: return
            renderPdf(uri)
        }
    }

    // Fungsi membaca dan menampilkan halaman pertama PDF
 
private fun renderPdf(uri: Uri) {
    try {
        contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->

            PdfRenderer(pfd).use { renderer ->

                if (renderer.pageCount == 0) {
                    throw Exception("PDF tidak memiliki halaman")
                }

                renderer.openPage(0).use { page ->

                    // Render mengikuti skala aplikasi web: 150 / 72
                    val renderWidth =
                        (page.width * 150f / 72f).toInt()

                    val renderHeight =
                        (page.height * 150f / 72f).toInt()

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

                    // Proses gambar resi
                    val result = processReceipt(sourceBitmap)

                    processedBitmap = result

                    // Tampilkan hasil pengolahan
                    pdfPreview.setImageBitmap(result)

                    status.text =
                        "Resi berhasil diproses: ${result.width} x ${result.height} px"
                }
            }
        } ?: throw Exception("Tidak dapat membuka file PDF")

    } catch (e: Exception) {
        status.text = "Gagal memproses PDF: ${e.message}"
    }
}


private fun processReceipt(source: Bitmap): Bitmap {

    // 1. Crop area resi
    val cropX = 0
    val cropY = 35
    val cropWidth = source.width
    val cropHeight = minOf(450, source.height - cropY)

    require(cropHeight > 0) {
        "Ukuran PDF terlalu kecil untuk crop"
    }

    val cropped = Bitmap.createBitmap(
        source,
        cropX,
        cropY,
        cropWidth,
        cropHeight
    )


    // 2. Trim area putih, abaikan garis bingkai tipis
    val rowCounts = IntArray(cropped.height)
    val colCounts = IntArray(cropped.width)

    for (y in 0 until cropped.height) {
        for (x in 0 until cropped.width) {
            val pixel = cropped.getPixel(x, y)

            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)

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

    // Abaikan garis panjang dari bingkai PDF
for (y in rowCounts.indices) {
    if (rowCounts[y] > 2 &&
        rowCounts[y] < cropped.width * 0.80
    ) {
        minY = minOf(minY, y)
        maxY = maxOf(maxY, y)
    }
}

for (x in colCounts.indices) {
    if (colCounts[x] > 2 &&
        colCounts[x] < cropped.height * 0.80
    ) {
        minX = minOf(minX, x)
        maxX = maxOf(maxX, x)
    }
}

    require(maxX >= minX && maxY >= minY) {
        "Isi resi tidak ditemukan"
    }

    val trimmed = Bitmap.createBitmap(
        cropped,
        minX,
        minY,
        maxX - minX + 1,
        maxY - minY + 1
    )


    // 3. Rotasi -90 derajat
    val rotated = rotateBitmap(trimmed, -90f)

    status.text =
    "Trim: ${trimmed.width}x${trimmed.height} | Rotasi: ${rotated.width}x${rotated.height}"

    // 4. Resize lebar menjadi 384 pixel
    val newHeight = (
        rotated.height * 384f / rotated.width
    ).toInt().coerceAtLeast(1)

    val resized = Bitmap.createScaledBitmap(
        rotated,
        384,
        newHeight,
        true
    )

    // 5. Grayscale dan threshold 65%
    val binary = Bitmap.createBitmap(
        384,
        newHeight,
        Bitmap.Config.ARGB_8888
    )

    for (y in 0 until newHeight) {
        for (x in 0 until 384) {
            val pixel = resized.getPixel(x, y)

            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)

            val gray = (
                0.299 * r +
                0.587 * g +
                0.114 * b
            ).toInt()

            val value = if (gray < 255 * 0.65) 0 else 255

            binary.setPixel(
                x,
                y,
                android.graphics.Color.rgb(value, value, value)
            )
        }
    }

    // 6. Rotasi 180 derajat
    val finalRotation = rotateBitmap(binary, 180f)

    // 7. Tambahkan border hitam 2 pixel kiri dan kanan
    val result = Bitmap.createBitmap(
        384,
        finalRotation.height,
        Bitmap.Config.ARGB_8888
    )

    val canvas = android.graphics.Canvas(result)

    canvas.drawColor(android.graphics.Color.WHITE)

    val paint = android.graphics.Paint()

    canvas.drawBitmap(finalRotation, 0f, 0f, paint)

    val borderPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
    }

    canvas.drawRect(
        0f, 0f, 2f, result.height.toFloat(), borderPaint
    )

    canvas.drawRect(
        382f, 0f, 384f, result.height.toFloat(), borderPaint
    )

    return result
}

private fun rotateBitmap(
    source: Bitmap,
    degrees: Float
): Bitmap {
    val matrix = android.graphics.Matrix().apply {
        postRotate(degrees)
    }

    return Bitmap.createBitmap(
        source,
        0,
        0,
        source.width,
        source.height,
        matrix,
        true
    )
}

private fun addWatermark(
    source: Bitmap,
    watermark: String
): Bitmap {

    val result = Bitmap.createBitmap(
        source.width,
        source.height + 55,
        Bitmap.Config.ARGB_8888
    )

    val canvas = Canvas(result)
    canvas.drawColor(android.graphics.Color.WHITE)

    canvas.drawBitmap(source, 0f, 0f, null)

    if (watermark.isNotBlank()) {

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 26f
            typeface = Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )
            textAlign = Paint.Align.CENTER
        }

        val maxWidth = source.width - 16f
        val measuredWidth = paint.measureText(watermark)

        if (measuredWidth > maxWidth) {
            paint.textSize *= maxWidth / measuredWidth
        }

        canvas.drawText(
            watermark,
            source.width / 2f,
            source.height + 36f,
            paint
        )
    }

    return result
}


}
