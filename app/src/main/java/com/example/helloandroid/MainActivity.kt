
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
import android.widget.FrameLayout
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.widget.PopupWindow

import android.app.PendingIntent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager


class MainActivity : Activity() {

    private val sppUUID = UUID.fromString(
        "00001101-0000-1000-8000-00805F9B34FB"
    )

private var adapter: BluetoothAdapter? = null
private var printer: BluetoothDevice? = null
private var socket: BluetoothSocket? = null


// USB PRINTER
private var usbManager: UsbManager? = null
private var usbDevice: UsbDevice? = null
private var usbConnection: UsbDeviceConnection? = null
private var usbInterface: UsbInterface? = null
private var usbEndpointOut: UsbEndpoint? = null
private var usbEndpointIn: UsbEndpoint? = null

private var usbPermissionGranted = false
private var usbPermissionReceiverRegistered = false
    

private lateinit var pdfPreview: ImageView
private lateinit var previewPlaceholder: LinearLayout
private var processedBitmap: Bitmap? = null
private lateinit var status: TextView
private lateinit var deviceInfo: TextView

private lateinit var connectButton: Button
private lateinit var selectPdfButton: Button
private lateinit var printButton: Button
private lateinit var searchButton: TextView

private var usbPermissionReceiverRegistered = false
private var usbDeviceReceiverRegistered = false

    companion object {
        private const val REQUEST_BT = 100
        private const val REQUEST_PDF = 200
        private const val APP_WATERMARK =
        "Generate by: JZ Receipt Printer"
        private const val ACTION_USB_PERMISSION =
        "com.example.helloandroid.USB_PERMISSION"
        private const val REQUEST_USB = 300
    }

// Daftar perangkat yang ditemukan saat scan
private val discoveredDevices = mutableListOf<BluetoothDevice>()

// Status penerima broadcast
private var receiverRegistered = false

// Penerima hasil Bluetooth Discovery
private val discoveryReceiver = object : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        when (intent.action) {

            BluetoothDevice.ACTION_FOUND -> {

                val device: BluetoothDevice? =
                    if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE
                        )
                    }

                if (device != null) {
                    if (discoveredDevices.none {
                        it.address == device.address
                    }) {
                        discoveredDevices.add(device)

                        deviceInfo.text =
                            "Perangkat ditemukan: ${discoveredDevices.size}"
                    }
                }
            }

            BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                finishBluetoothScan()
            }
        }
    }
}


// PENERIMA IZIN USB
private val usbPermissionReceiver = object : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (intent.action == ACTION_USB_PERMISSION) {

            val device: UsbDevice? =
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(
                        UsbManager.EXTRA_DEVICE,
                        UsbDevice::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(
                        UsbManager.EXTRA_DEVICE
                    )
                }

            val granted = intent.getBooleanExtra(
                UsbManager.EXTRA_PERMISSION_GRANTED,
                false
            )

            if (device != null && granted) {
                usbDevice = device
                usbPermissionGranted = true
                connectUsbPrinter()

                runOnUiThread {
                    deviceInfo.text =
                        "USB: ${device.productName ?: "Printer USB"}"

                    status.text = "Izin USB diberikan"
                }

            } else {
                usbPermissionGranted = false

                runOnUiThread {
                    status.text = "Izin akses USB ditolak"
                }
            }
        }
    }
}

// DETEKSI USB DIPASANG / DILEPAS
private val usbDeviceReceiver = object : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        when (intent.action) {

            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                scanUsbDevices()
            }

            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                disconnectUsbPrinter()
                scanUsbDevices()
            }
        }
    }
}

// SCAN USB PRINTER
private fun scanUsbDevices() {

    usbManager = getSystemService(
        Context.USB_SERVICE
    ) as UsbManager

    val devices = usbManager!!.deviceList.values

    var foundDevice: UsbDevice? = null
    var foundInterface: UsbInterface? = null
    var foundEndpointOut: UsbEndpoint? = null
    var foundEndpointIn: UsbEndpoint? = null

    for (device in devices) {

        for (i in 0 until device.interfaceCount) {

            val usbIntf = device.getInterface(i)

            // Periksa USB Printer Class
            if (usbIntf.interfaceClass ==
                UsbConstants.USB_CLASS_PRINTER
            ) {

                var endpointOut: UsbEndpoint? = null
                var endpointIn: UsbEndpoint? = null

                for (j in 0 until usbIntf.endpointCount) {

                    val endpoint = usbIntf.getEndpoint(j)

                    if (endpoint.type ==
                        UsbConstants.USB_ENDPOINT_XFER_BULK
                    ) {

                        if (endpoint.direction ==
                            UsbConstants.USB_DIR_OUT
                        ) {
                            endpointOut = endpoint
                        }

                        if (endpoint.direction ==
                            UsbConstants.USB_DIR_IN
                        ) {
                            endpointIn = endpoint
                        }
                    }
                }

                // Printer harus memiliki endpoint OUT
                if (endpointOut != null) {

                    foundDevice = device
                    foundInterface = usbIntf
                    foundEndpointOut = endpointOut
                    foundEndpointIn = endpointIn

                    break
                }
            }
        }

        if (foundDevice != null) break
    }

    if (foundDevice != null) {

        usbDevice = foundDevice
        usbInterface = foundInterface
        usbEndpointOut = foundEndpointOut
        usbEndpointIn = foundEndpointIn

        deviceInfo.text = "USB: Printer terdeteksi"
        status.text = "Printer USB menunggu koneksi"

        requestUsbPermission(foundDevice)

    } else {

        deviceInfo.text = "USB: Tidak ada printer ditemukan"
        status.text = "Menunggu perangkat USB"
    }
}

// MEMINTA IZIN AKSES USB
private fun requestUsbPermission(device: UsbDevice) {

    val manager = usbManager ?: return

    // Periksa apakah izin sudah pernah diberikan
    if (manager.hasPermission(device)) {
        usbDevice = device
        usbPermissionGranted = true
        status.text = "Izin USB sudah diberikan"
        return
    }

    val permissionIntent = Intent(ACTION_USB_PERMISSION).apply {
        setPackage(packageName)
    }

    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        })

    val pendingIntent = PendingIntent.getBroadcast(
        this,
        REQUEST_USB,
        permissionIntent,
        flags
    )

    status.text = "Menunggu izin akses USB..."

    manager.requestPermission(device, pendingIntent)
}

// MEMBUKA KONEKSI USB PRINTER
private fun connectUsbPrinter() {

    val manager = usbManager ?: return
    val device = usbDevice ?: return
    val intf = usbInterface ?: return
    val endpoint = usbEndpointOut ?: return

    if (!manager.hasPermission(device)) {
        status.text = "Izin USB belum diberikan"
        requestUsbPermission(device)
        return
    }

    try {
        // Buka perangkat USB
        usbConnection = manager.openDevice(device)

        if (usbConnection == null) {
            status.text = "Gagal membuka printer USB"
            return
        }

        // Ambil alih interface printer
        val claimed = usbConnection!!.claimInterface(intf, true)

        if (!claimed) {
            usbConnection!!.close()
            usbConnection = null
            status.text = "Gagal mengambil interface USB"
            return
        }

        usbPermissionGranted = true

        deviceInfo.text = "USB: ${device.productName ?: "Printer USB"}"
        status.text = "Printer USB terhubung"

    } catch (e: Exception) {
        usbConnection?.close()
        usbConnection = null
        status.text = "Koneksi USB gagal: ${e.message}"
    }
}

// KIRIM DATA KE PRINTER USB
private fun sendUsbData(data: ByteArray): Boolean {

    val connection = usbConnection ?: run {
        status.text = "Printer USB belum terhubung"
        return false
    }

    val endpoint = usbEndpointOut ?: run {
        status.text = "USB OUT endpoint tidak ditemukan"
        return false
    }

    return try {
        var offset = 0

        while (offset < data.size) {

            val length = minOf(4096, data.size - offset)

            val chunk = data.copyOfRange(
                offset,
                offset + length
            )

            val sent = connection.bulkTransfer(
                endpoint,
                chunk,
                chunk.size,
                5000
            )

            if (sent <= 0) {
                status.text = "Pengiriman data USB gagal"
                return false
            }

            offset += sent
        }

        true

    } catch (e: Exception) {
        status.text = "Kesalahan USB: ${e.message}"
        false
    }
}

// MEMUTUS KONEKSI USB
private fun disconnectUsbPrinter() {

    try {
        if (usbConnection != null) {
            usbInterface?.let {
                usbConnection?.releaseInterface(it)
            }

            usbConnection?.close()
        }
    } catch (e: Exception) {
        // Abaikan jika perangkat sudah dilepas
    }

    usbConnection = null
    usbDevice = null
    usbInterface = null
    usbEndpointOut = null
    usbEndpointIn = null

    usbPermissionGranted = false

    status.text = "USB dilepas"
    deviceInfo.text = "Printer: Belum terhubung"
}

  // onCreate start

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // DAFTARKAN USB PERMISSION RECEIVER
val usbPermissionFilter = IntentFilter(ACTION_USB_PERMISSION)

if (Build.VERSION.SDK_INT >= 33) {
    registerReceiver(
        usbPermissionReceiver,
        usbPermissionFilter,
        Context.RECEIVER_NOT_EXPORTED
    )
} else {
    registerReceiver(
        usbPermissionReceiver,
        usbPermissionFilter
    )
}

// DAFTARKAN USB DEVICE RECEIVER
val usbDeviceFilter = IntentFilter().apply {
    addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
    addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
}

if (Build.VERSION.SDK_INT >= 33) {
    registerReceiver(
        usbDeviceReceiver,
        usbDeviceFilter,
        Context.RECEIVER_NOT_EXPORTED
    )
} else {
    registerReceiver(
        usbDeviceReceiver,
        usbDeviceFilter
    )
}

usbPermissionReceiverRegistered = true
usbDeviceReceiverRegistered = true

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

    // efek ronded
    fun rippleRounded(color: Int, radius: Float): RippleDrawable {
    val rippleColor = ColorStateList.valueOf(
        Color.argb(70, 255, 255, 255)
    )

    return RippleDrawable(
        rippleColor,
        rounded(color, radius),
        rounded(Color.WHITE, radius)
    )
    }

    
// ROOT LAYOUT
val rootLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setBackgroundColor(navy)
}

// HEADER NAVBAR
val header = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    setPadding(dp(18), dp(10), dp(18), dp(10))
    setBackgroundColor(navy)
}

// LOGO
val logo = TextView(this).apply {
    text = "🖨"
    textSize = 25f
    gravity = Gravity.CENTER
    setTextColor(Color.WHITE)
    background = rounded(blue, 14f)
    includeFontPadding = false
}

// JUDUL
val headerTitle = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER_VERTICAL
    setPadding(dp(12), 0, 0, 0)
}

val appName = TextView(this).apply {
    text = "JZ RECEIPT"
    textSize = 17f
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(Color.WHITE)
}

val appSubtitle = TextView(this).apply {
    text = "THERMAL PRINT STUDIO"
    textSize = 9f
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(muted)
}

headerTitle.addView(appName)
headerTitle.addView(appSubtitle)

// TOMBOL HEADER
val menuButton = TextView(this).apply {
    text = "⋮"
    textSize = 30f
    gravity = Gravity.CENTER
    setTextColor(Color.WHITE)
    background = rippleRounded(cardColor, 12f)
    isClickable = true
    isFocusable = true
}


 // POPUP MENU
val popupMenu = PopupWindow(this).apply {
    val menuLayout = LinearLayout(this@MainActivity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(
            dp(8), dp(8),
            dp(8), dp(8)
        )
        background = rounded(cardColor, 14f)
    }

    val menuItems = listOf(
        "ⓘ   About",
        "🖨   Printer",
        "❔   Help",
        "⌘   GitHub"
    )

    menuItems.forEach { item ->
        val menuItem = TextView(this@MainActivity).apply {
            text = item
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(24), dp(12))
            isClickable = true
            isFocusable = true
            background = rippleRounded(cardColor, 8f)
        }

        menuLayout.addView(
            menuItem,
            LinearLayout.LayoutParams(
                dp(180), dp(46)
            )
        )
    }

    contentView = menuLayout
    width = dp(196)
    height = dp(208)
    isFocusable = true
    elevation = dp(8).toFloat()
    setBackgroundDrawable(
        rounded(cardColor, 14f)
    )
}

menuButton.setOnClickListener {
    if (popupMenu.isShowing) {
        popupMenu.dismiss()
    } else {
        popupMenu.showAsDropDown(
            menuButton,
            0,
            dp(4),
            Gravity.END
        )
    }
}

// SUSUN HEADER
header.addView(
    logo,
    LinearLayout.LayoutParams(dp(44), dp(44))
)

header.addView(
    headerTitle,
    LinearLayout.LayoutParams(0, dp(44), 1f)
)

val menuParams = LinearLayout.LayoutParams(
    dp(42), dp(42)
)

header.addView(menuButton, menuParams)

// GARIS PEMISAH
val headerLine = View(this).apply {
    setBackgroundColor(Color.rgb(64, 83, 106))
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


    // PANEL INFORMASI PRINTER
    val connectionInfoBox = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))

        background = GradientDrawable().apply {
            setColor(cardColor)
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), green)
        }
    }

    deviceInfo = TextView(this).apply {
        text = "Printer: Belum ditemukan"
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(white)
    }

    status = TextView(this).apply {
        text = "Status: Belum terhubung"
        textSize = 13f
        setTextColor(muted)
        setPadding(0, dp(8), 0, 0)
    }

    connectionInfoBox.addView(deviceInfo)
    connectionInfoBox.addView(status)

    connectionPanel.addView(
        connectionInfoBox,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(14)
        }
    )


// Tombol pencarian
searchButton = TextView(this).apply {
    text = "\uD83D\uDD0D  SCAN DEVICE"
    textSize = 14f
    typeface = Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER
    setTextColor(Color.WHITE)

    background = rippleRounded(blue, 14f)

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
    text = "\uD83D\uDD17  CONNECT PRINTER"
    isAllCaps = false
    textSize = 14f
    typeface = Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER

    setTextColor(Color.WHITE)
    background = rippleRounded(Color.rgb(48, 79, 110), 14f)
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

// Panel preview
val previewPanel = FrameLayout(this).apply {
    background = rounded(white, 16f)
    minimumHeight = dp(280)
    setPadding(dp(12), dp(12), dp(12), dp(12))
}

// Placeholder awal
previewPlaceholder = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER
    translationY = dp(30).toFloat()
}

// Ikon kertas
val paperIcon = TextView(this).apply {
    text = "📄"
    textSize = 42f
    gravity = Gravity.CENTER
}

previewPlaceholder.addView(paperIcon)

fun placeholderText(value: String, size: Float, color: Int): TextView {
    return TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
    }
}

previewPlaceholder.addView(
    placeholderText("Receipt Preview", 17f, Color.rgb(55, 65, 81))
)

previewPlaceholder.addView(
    placeholderText(
        "Your processed receipt appears here",
        12f,
        Color.rgb(145, 155, 165)
    )
)

// Garis-garis ilustrasi kertas
for (i in 1..4) {
    val line = View(this).apply {
        background = rounded(Color.rgb(225, 230, 235), 4f)
    }

    previewPlaceholder.addView(
        line,
        LinearLayout.LayoutParams(
            dp(if (i == 4) 90 else 150),
            dp(6)
        ).apply {
            topMargin = dp(10)
        }
    )
}

// Tampilkan placeholder
previewPanel.addView(
    previewPlaceholder,
    FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    )
)

// Gambar hasil PDF
pdfPreview = ImageView(this).apply {
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

layout.addView(
    previewPanel,
    LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
)

space(18)

    // Tombol pilih PDF
selectPdfButton = Button(this).apply {
    text = "📄 SELECT PDF"
    isAllCaps = false
    textSize = 14f
    typeface = Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER

    setTextColor(Color.WHITE)
    background = rippleRounded(blue, 14f)
    backgroundTintList = null

    minHeight = dp(56)
    setPadding(dp(12), dp(4), dp(12), dp(4))

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
            dp(56)
        )
    )

    // Tombol cetak
    printButton = Button(this).apply {
    text = "🖨 PRINT"
    isAllCaps = false
    textSize = 14f
    typeface = Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER

    setTextColor(Color.WHITE)
    background = rippleRounded(green, 14f)
    backgroundTintList = null

    minHeight = dp(56)
    setPadding(dp(12), dp(4), dp(12), dp(4))        

        setOnClickListener {
            printReceipt()
        }
    }

    val printParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        dp(58)
    ).apply {
        topMargin = dp(10)
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

    // SCROLL KONTEN
    val scrollView = ScrollView(this).apply {
    setFillViewport(true)
    setBackgroundColor(navy)
    addView(layout)
}

// GARIS PEMISAH HEADER
headerLine.layoutParams = LinearLayout.LayoutParams(
    LinearLayout.LayoutParams.MATCH_PARENT,
    dp(1)
)

// SUSUN ROOT LAYOUT
rootLayout.addView(header)

rootLayout.addView(headerLine)

rootLayout.addView(
    scrollView,
    LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        0,
        1f
    )
)

// TAMPILKAN
setContentView(rootLayout)
// CEK PRINTER USB SAAT APLIKASI DIBUKA
scanUsbDevices()
}

  // onCreate end

private fun hasBluetoothPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= 31) {
        checkSelfPermission(
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED &&
        checkSelfPermission(
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        checkSelfPermission(
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private fun requestBluetoothPermission() {
    if (Build.VERSION.SDK_INT >= 31) {
        requestPermissions(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ),
            REQUEST_BT
        )
    } else {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
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

    // PERIKSA APAKAH USB SEDANG AKTIF
private fun isUsbPrinterConnected(): Boolean {
    return usbConnection != null && usbDevice != null
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

        discoveredDevices.clear()

        status.text = "Memindai perangkat Bluetooth..."
        deviceInfo.text = "Sedang mencari perangkat di sekitar"

        if (bt.isDiscovering) {
            bt.cancelDiscovery()
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                discoveryReceiver,
                filter,
                Context.RECEIVER_EXPORTED
            )
        } else {
            registerReceiver(discoveryReceiver, filter)
        }

        receiverRegistered = true

        val started = bt.startDiscovery()

        if (!started) {
            finishBluetoothScan()
            status.text = "Gagal memulai pemindaian"
        }

    } catch (e: Exception) {
        status.text = "Error scan: ${e.message}"

        if (receiverRegistered) {
            unregisterReceiver(discoveryReceiver)
            receiverRegistered = false
        }
    }
}


private fun showDevicePicker() {

    val devices = discoveredDevices.toList()

    val deviceNames = devices.map { device ->
        "${device.name ?: "Unknown"}\n${device.address}"
    }.toTypedArray()

    AlertDialog.Builder(this)
        .setTitle("Pilih Printer Bluetooth")
        .setItems(deviceNames) { _, which ->

            val selectedDevice = devices[which]

            printer = selectedDevice

            deviceInfo.text =
                "Printer dipilih: ${selectedDevice.name ?: "Unknown"}"

            status.text = "Siap dihubungkan"

        }
        .setNegativeButton("BATAL", null)
        .show()
}


private fun finishBluetoothScan() {

    if (receiverRegistered) {
        unregisterReceiver(discoveryReceiver)
        receiverRegistered = false
    }

    status.text = "Pemindaian selesai"

    if (discoveredDevices.isEmpty()) {
        deviceInfo.text = "Tidak ada perangkat ditemukan"
        status.text = "Coba scan kembali"
        return
    }

    showDevicePicker()
}

    private fun connectPrinter() {
        if (isUsbPrinterConnected()) {
    status.text = "Cabut USB OTG terlebih dahulu"
    return
        }
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

    // fungsi dedtroy
    override fun onDestroy() {

    // TUTUP KONEKSI BLUETOOTH
    try {
        socket?.close()
    } catch (_: Exception) {
    }

    // TUTUP KONEKSI USB
    disconnectUsbPrinter()

    // LEPAS USB PERMISSION RECEIVER
    if (usbPermissionReceiverRegistered) {
        try {
            unregisterReceiver(usbPermissionReceiver)
        } catch (_: Exception) {
        }

        usbPermissionReceiverRegistered = false
    }

    // LEPAS USB DEVICE RECEIVER
    if (usbDeviceReceiverRegistered) {
        try {
            unregisterReceiver(usbDeviceReceiver)
        } catch (_: Exception) {
        }

        usbDeviceReceiverRegistered = false
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
                    
                    // Sembunyikan placeholder
                    previewPlaceholder.visibility = View.GONE
                    pdfPreview.visibility = View.VISIBLE

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
