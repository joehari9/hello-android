
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

class MainActivity : Activity() {

    private val printerName = "JZ-MP250"

    private val sppUUID = UUID.fromString(
        "00001101-0000-1000-8000-00805F9B34FB"
    )

    private var adapter: BluetoothAdapter? = null
    private var printer: BluetoothDevice? = null
    private var socket: BluetoothSocket? = null
    private lateinit var pdfPreview: ImageView
    private lateinit var status: TextView
    private lateinit var deviceInfo: TextView
    private lateinit var connectButton: Button
    private lateinit var printButton: Button

    companion object {
        private const val REQUEST_BT = 100
        private const val REQUEST_PDF = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manager = getSystemService(BluetoothManager::class.java)
        adapter = manager.adapter

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(30, 30, 30, 30)
        }

        val title = TextView(this).apply {
            text = "JZ PRINTER TEST"
            textSize = 25f
            gravity = Gravity.CENTER
        }

        deviceInfo = TextView(this).apply {
            text = "Printer: Belum ditemukan"
            textSize = 17f
            gravity = Gravity.CENTER
        }

        status = TextView(this).apply {
            text = "Status: Belum terhubung"
            textSize = 16f
            gravity = Gravity.CENTER
        }

        val searchButton = Button(this).apply {
            text = "Cari Printer"
            setOnClickListener {
                findPrinter()
            }
        }

        connectButton = Button(this).apply {
            text = "Connect"
            setOnClickListener {
                connectPrinter()
            }
        }

        printButton = Button(this).apply {
            text = "Test Print"
            isEnabled = false
            setOnClickListener {
                testPrint()
            }
        }
val selectPdfButton = Button(this).apply {
    text = "Pilih PDF Resi"

    setOnClickListener {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }

        startActivityForResult(intent, REQUEST_PDF)
    }
}

pdfPreview = ImageView(this).apply {
    adjustViewBounds = true
    scaleType = ImageView.ScaleType.FIT_CENTER
}

        layout.addView(title)
        layout.addView(deviceInfo)
        layout.addView(status)
        layout.addView(searchButton)
        layout.addView(connectButton)
        layout.addView(printButton)
        layout.addView(selectPdfButton)
        layout.addView(
    pdfPreview,
    LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
)

        setContentView(layout)
    }

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

                        val bitmap = Bitmap.createBitmap(
                            page.width * 2,
                            page.height * 2,
                            Bitmap.Config.ARGB_8888
                        )

                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )

                        pdfPreview.setImageBitmap(bitmap)

                        status.text =
                            "PDF berhasil dibuka. Siap diproses."
                    }
                }
            } ?: throw Exception("Tidak dapat membuka file PDF")

        } catch (e: Exception) {
            status.text = "Gagal membaca PDF: ${e.message}"
        }
    }
}
