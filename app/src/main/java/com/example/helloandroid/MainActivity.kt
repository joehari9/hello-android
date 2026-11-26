package com.example.helloandroid

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.pdf.PdfRenderer
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

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
    private var usbDeviceReceiverRegistered = false

    // UI REFERENCES
    private lateinit var pdfPreview: ImageView
    private lateinit var previewPlaceholder: LinearLayout
    private var processedBitmap: Bitmap? = null
    private lateinit var status: TextView
    private lateinit var deviceInfo: TextView

    // UI ENHANCEMENT REFERENCES
    private lateinit var statusDot: View
    private lateinit var statusBadgeText: TextView
    private lateinit var statusBadgeBox: LinearLayout
    private lateinit var connectionInfoBox: LinearLayout
    private lateinit var resolutionBadge: TextView

    private lateinit var connectButton: LinearLayout
    private lateinit var selectPdfButton: LinearLayout
    private lateinit var printButton: LinearLayout
    private lateinit var searchButton: LinearLayout
    private var backPressedTime = 0L

    // THEME PALETTE (MODERN DARK INDUSTRIAL STUDIO)
    private val colBgPrimary = Color.parseColor("#0B1120")      // Deep Slate Navy
    private val colSurface = Color.parseColor("#131C31")        // Elevated Card Surface
    private val colSurfaceElevated = Color.parseColor("#1A2642")// Inner Panel / Button Secondary
    private val colBorder = Color.parseColor("#263554")         // Subtle Card Border
    private val colAccentBlue = Color.parseColor("#2563EB")     // Electric Royal Blue
    private val colAccentCyan = Color.parseColor("#0EA5E9")     // Cyan Glow
    private val colSuccess = Color.parseColor("#10B981")        // Emerald Green
    private val colSuccessBg = Color.parseColor("#064E3B")      // Emerald Dark Tint
    private val colWarning = Color.parseColor("#F59E0B")        // Amber Warning
    private val colWarningBg = Color.parseColor("#451A03")      // Amber Dark Tint
    private val colTextPrimary = Color.parseColor("#F8FAFC")    // Crisp White
    private val colTextSecondary = Color.parseColor("#94A3B8")  // Slate Muted
    private val colTextMuted = Color.parseColor("#64748B")      // Subtle Caption

    companion object {
        private const val REQUEST_BT = 100
        private const val REQUEST_PDF = 200
        private const val APP_WATERMARK = "Generate by: JZ Receipt Printer"
        private const val ACTION_USB_PERMISSION = "com.example.helloandroid.USB_PERMISSION"
        private const val REQUEST_USB = 300
        private const val CHANNEL_ID = "printer_connection"
        private const val REQUEST_NOTIFICATION = 400
    }

    // Daftar perangkat yang ditemukan saat scan
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private var receiverRegistered = false

    // Helper untuk memperbarui indikator status koneksi secara visual
    private fun updateConnectionVisualState(state: String) {
        if (!::statusDot.isInitialized) return
        when (state) {
            "CONNECTED" -> {
                statusBadgeText.text = "ONLINE"
                statusBadgeText.setTextColor(colSuccess)
                (statusDot.background as? GradientDrawable)?.setColor(colSuccess)
                (statusBadgeBox.background as? GradientDrawable)?.apply {
                    setColor(colSuccessBg)
                    setStroke(dp(1), colSuccess)
                }
                (connectionInfoBox.background as? GradientDrawable)?.setStroke(dp(1), colSuccess)
            }
            "CONNECTING" -> {
                statusBadgeText.text = "BUSY"
                statusBadgeText.setTextColor(colWarning)
                (statusDot.background as? GradientDrawable)?.setColor(colWarning)
                (statusBadgeBox.background as? GradientDrawable)?.apply {
                    setColor(colWarningBg)
                    setStroke(dp(1), colWarning)
                }
                (connectionInfoBox.background as? GradientDrawable)?.setStroke(dp(1), colWarning)
            }
            else -> {
                statusBadgeText.text = "OFFLINE"
                statusBadgeText.setTextColor(colTextSecondary)
                (statusDot.background as? GradientDrawable)?.setColor(colTextMuted)
                (statusBadgeBox.background as? GradientDrawable)?.apply {
                    setColor(colBgPrimary)
                    setStroke(dp(1), colBorder)
                }
                (connectionInfoBox.background as? GradientDrawable)?.setStroke(dp(1), colBorder)
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int = Color.TRANSPARENT, strokeWidthDp: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeWidthDp > 0 && strokeColor != Color.TRANSPARENT) {
                setStroke(dp(strokeWidthDp), strokeColor)
            }
        }
    }

    private fun gradientRounded(startColor: Int, endColor: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(startColor, endColor)
        ).apply {
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun rippleRounded(color: Int, radiusDp: Int, strokeColor: Int = Color.TRANSPARENT, strokeWidthDp: Int = 0): RippleDrawable {
        val rippleColor = ColorStateList.valueOf(Color.argb(55, 255, 255, 255))
        return RippleDrawable(
            rippleColor,
            rounded(color, radiusDp, strokeColor, strokeWidthDp),
            rounded(Color.WHITE, radiusDp)
        )
    }

    private fun rippleGradient(startColor: Int, endColor: Int, radiusDp: Int): RippleDrawable {
        val rippleColor = ColorStateList.valueOf(Color.argb(65, 255, 255, 255))
        return RippleDrawable(
            rippleColor,
            gradientRounded(startColor, endColor, radiusDp),
            rounded(Color.WHITE, radiusDp)
        )
    }

    // Penerima hasil Bluetooth Discovery
    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
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
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }

                    if (device != null) {
                        if (discoveredDevices.none { it.address == device.address }) {
                            discoveredDevices.add(device)
                            deviceInfo.text = "Ditemukan: ${discoveredDevices.size} perangkat"
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
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_USB_PERMISSION) {
                val device: UsbDevice? =
                    if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(
                            UsbManager.EXTRA_DEVICE,
                            UsbDevice::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
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
                        deviceInfo.text = "USB: ${device.productName ?: "Printer USB"}"
                        status.text = "Izin USB diberikan"
                    }
                } else {
                    usbPermissionGranted = false
                    runOnUiThread {
                        status.text = "Izin akses USB ditolak"
                        updateConnectionVisualState("DISCONNECTED")
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
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = usbManager!!.deviceList.values

        var foundDevice: UsbDevice? = null
        var foundInterface: UsbInterface? = null
        var foundEndpointOut: UsbEndpoint? = null
        var foundEndpointIn: UsbEndpoint? = null

        for (device in devices) {
            for (i in 0 until device.interfaceCount) {
                val usbIntf = device.getInterface(i)
                if (usbIntf.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                    var endpointOut: UsbEndpoint? = null
                    var endpointIn: UsbEndpoint? = null

                    for (j in 0 until usbIntf.endpointCount) {
                        val endpoint = usbIntf.getEndpoint(j)
                        if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                            if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                                endpointOut = endpoint
                            }
                            if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                                endpointIn = endpoint
                            }
                        }
                    }

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
            updateConnectionVisualState("CONNECTING")

            requestUsbPermission(foundDevice)
        } else {
            if (socket?.isConnected != true) {
                deviceInfo.text = "Belum ada printer terhubung"
                status.text = "Siap memindai Bluetooth atau sambungkan USB OTG"
                updateConnectionVisualState("DISCONNECTED")
            }
        }
    }

    // MEMINTA IZIN AKSES USB
    private fun requestUsbPermission(device: UsbDevice) {
        val manager = usbManager ?: return

        if (manager.hasPermission(device)) {
            usbDevice = device
            usbPermissionGranted = true
            connectUsbPrinter()
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
        updateConnectionVisualState("CONNECTING")
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
            usbConnection = manager.openDevice(device)
            if (usbConnection == null) {
                status.text = "Gagal membuka printer USB"
                updateConnectionVisualState("DISCONNECTED")
                return
            }

            val claimed = usbConnection!!.claimInterface(intf, true)
            if (!claimed) {
                usbConnection!!.close()
                usbConnection = null
                status.text = "Gagal mengambil interface USB"
                updateConnectionVisualState("DISCONNECTED")
                return
            }

            try {
                socket?.close()
            } catch (_: Exception) {
            }
            socket = null

            usbPermissionGranted = true

            deviceInfo.text = "USB: ${device.productName ?: "Printer USB"}"
            status.text = "Terhubung via USB OTG • Siap cetak"
            updateConnectionVisualState("CONNECTED")
            printButton.isEnabled = true
            updateBluetoothButtons()

            showPrinterConnectedNotification(
                device.productName ?: "Printer USB",
                "USB"
            )
        } catch (e: Exception) {
            usbConnection?.close()
            usbConnection = null
            status.text = "Koneksi USB gagal: ${e.message}"
            updateConnectionVisualState("DISCONNECTED")
        }
    }

    // KIRIM DATA KE PRINTER USB
    private fun sendUsbData(data: ByteArray): Boolean {
        val connection = usbConnection ?: return false
        val endpoint = usbEndpointOut ?: return false

        return try {
            var offset = 0
            while (offset < data.size) {
                val length = minOf(4096, data.size - offset)
                val chunk = data.copyOfRange(offset, offset + length)
                val sent = connection.bulkTransfer(endpoint, chunk, chunk.size, 5000)
                if (sent <= 0) {
                    return false
                }
                offset += sent
            }
            true
        } catch (e: Exception) {
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
        }

        usbConnection = null
        usbDevice = null
        usbInterface = null
        usbEndpointOut = null
        usbEndpointIn = null
        usbPermissionGranted = false

        status.text = "Koneksi USB dilepas"
        deviceInfo.text = "Belum ada printer terhubung"
        updateConnectionVisualState("DISCONNECTED")
        printButton.isEnabled = false
        updateBluetoothButtons()
    }

    // NOTIFICATION
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Printer Connection",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Status koneksi printer"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION
                )
            }
        }
    }

    private fun showPrinterConnectedNotification(printerName: String, connectionType: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setPriority(Notification.PRIORITY_DEFAULT)
        }

        notification
            .setSmallIcon(R.drawable.ic_printer)
            .setContentTitle("JZ Receipt Printer")
            .setContentText("$printerName berhasil terhubung melalui $connectionType")
            .setAutoCancel(true)

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1001, notification.build())
    }

    // =========================================================================
    // ON CREATE (MODERN DARK INDUSTRIAL UI)
    // =========================================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Atur status bar & navigation bar menyatu dengan tema gelap
        window.statusBarColor = colBgPrimary
        window.navigationBarColor = colBgPrimary

        createNotificationChannel()
        requestNotificationPermission()

        // DAFTARKAN USB PERMISSION RECEIVER
        val usbPermissionFilter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(usbPermissionReceiver, usbPermissionFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbPermissionReceiver, usbPermissionFilter)
        }

        // DAFTARKAN USB DEVICE RECEIVER
        val usbDeviceFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(usbDeviceReceiver, usbDeviceFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbDeviceReceiver, usbDeviceFilter)
        }

        usbPermissionReceiverRegistered = true
        usbDeviceReceiverRegistered = true

        val manager = getSystemService(BluetoothManager::class.java)
        adapter = manager.adapter

        // ROOT LAYOUT
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colBgPrimary)
        }

        // ---------------------------------------------------------------------
        // 1. TOP APP BAR (ELEVATED HEADER)
        // ---------------------------------------------------------------------
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            setBackgroundColor(colBgPrimary)
        }

        // App Icon Badge dengan Gradient
        val logoContainer = FrameLayout(this).apply {
            background = gradientRounded(colAccentBlue, colAccentCyan, 14)
            elevation = dp(4).toFloat()
        }
        val logoIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_printer)
            setColorFilter(Color.WHITE)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        logoContainer.addView(
            logoIcon,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        // Judul & Subtitle Aplikasi
        val headerTitle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(8), 0)
        }

        val appName = TextView(this).apply {
            text = "JZ RECEIPT"
            textSize = 18f
            letterSpacing = 0.05f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setTextColor(colTextPrimary)
        }

        val appSubtitle = TextView(this).apply {
            text = "THERMAL PRINT STUDIO  •  58MM"
            textSize = 10f
            letterSpacing = 0.08f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colAccentCyan)
        }

        headerTitle.addView(appName)
        headerTitle.addView(appSubtitle)

        // Tombol Menu (Overflow)
        val menuButton = TextView(this).apply {
            text = "⋮"
            textSize = 22f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colTextPrimary)
            background = rippleRounded(colSurface, 12, colBorder, 1)
            isClickable = true
            isFocusable = true
        }

        // POPUP MENU MODERN
        lateinit var popupMenu: PopupWindow
        popupMenu = PopupWindow(this).apply {
            val menuLayout = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = rounded(colSurfaceElevated, 16, colBorder, 1)
            }

            val menuItems = listOf(
                "ⓘ   About App",
                "🖨   Printer Info",
                "❔   User Guide",
                "⌘   GitHub Repo"
            )

            menuItems.forEachIndexed { index, item ->
                val menuItem = TextView(this@MainActivity).apply {
                    text = item
                    textSize = 14f
                    setTextColor(colTextPrimary)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(10), dp(18), dp(10))
                    isClickable = true
                    isFocusable = true
                    background = rippleRounded(Color.TRANSPARENT, 10)
                }

                menuItem.setOnClickListener {
                    popupMenu.dismiss()
                    when (index) {
                        0 -> AboutDialog.show(this@MainActivity)
                        1 -> {
                            val usbActive = isUsbPrinterConnected()
                            val bluetoothActive = socket?.isConnected == true

                            val printerName = when {
                                usbActive -> usbDevice?.productName ?: "Printer USB"
                                bluetoothActive -> printer?.name ?: "Bluetooth Printer"
                                else -> "No Printer Connected"
                            }

                            val connectionType = when {
                                usbActive -> "USB"
                                bluetoothActive -> "Bluetooth"
                                else -> "None"
                            }

                            val connected = usbActive || bluetoothActive
                            PrinterDialog.show(this@MainActivity, printerName, connectionType, connected)
                        }
                        2 -> HelpDialog.show(this@MainActivity)
                        3 -> {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/"))
                            startActivity(intent)
                        }
                    }
                }

                menuLayout.addView(
                    menuItem,
                    LinearLayout.LayoutParams(dp(184), dp(44))
                )
            }

            contentView = menuLayout
            width = dp(200)
            height = LinearLayout.LayoutParams.WRAP_CONTENT
            isFocusable = true
            elevation = dp(12).toFloat()
            setBackgroundDrawable(rounded(colSurfaceElevated, 16, colBorder, 1))
        }

        menuButton.setOnClickListener {
            if (popupMenu.isShowing) {
                popupMenu.dismiss()
            } else {
                popupMenu.showAsDropDown(menuButton, 0, dp(6), Gravity.END)
            }
        }

        header.addView(logoContainer, LinearLayout.LayoutParams(dp(44), dp(44)))
        header.addView(headerTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(menuButton, LinearLayout.LayoutParams(dp(42), dp(42)))

        val headerLine = View(this).apply {
            setBackgroundColor(colBorder)
        }

        // ---------------------------------------------------------------------
        // KONTEN UTAMA DALAM SCROLLVIEW
        // ---------------------------------------------------------------------
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
            setBackgroundColor(colBgPrimary)
        }

        fun sectionHeader(titleText: String, badgeText: String? = null): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), 0, dp(2), dp(10))

                addView(TextView(this@MainActivity).apply {
                    text = titleText
                    textSize = 12f
                    letterSpacing = 0.08f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(colTextSecondary)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })

                if (badgeText != null) {
                    resolutionBadge = TextView(this@MainActivity).apply {
                        text = badgeText
                        textSize = 10f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(colAccentCyan)
                        setPadding(dp(8), dp(3), dp(8), dp(3))
                        background = rounded(colSurfaceElevated, 8, colBorder, 1)
                    }
                    addView(resolutionBadge)
                }
            }
        }

        // ---------------------------------------------------------------------
        // 2. KARTU KONEKSI PRINTER (HARDWARE CONNECTION)
        // ---------------------------------------------------------------------
        layout.addView(sectionHeader("HARDWARE CONNECTION"))

        val connectionPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(colSurface, 20, colBorder, 1)
            elevation = dp(4).toFloat()
        }

        // Box Status Perangkat & Live Indicator Pill
        connectionInfoBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(colSurfaceElevated, 14, colBorder, 1)
        }

        val topStatusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        deviceInfo = TextView(this).apply {
            text = "Printer: Belum ditemukan"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colTextPrimary)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Status Pill Badge (OFFLINE / BUSY / ONLINE)
        statusBadgeBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = rounded(colBgPrimary, 20, colBorder, 1)
        }

        statusDot = View(this).apply {
            background = rounded(colTextMuted, 10)
        }

        statusBadgeText = TextView(this).apply {
            text = "OFFLINE"
            textSize = 10f
            letterSpacing = 0.05f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colTextSecondary)
            setPadding(dp(6), 0, 0, 0)
        }

        statusBadgeBox.addView(statusDot, LinearLayout.LayoutParams(dp(7), dp(7)))
        statusBadgeBox.addView(statusBadgeText)

        topStatusRow.addView(deviceInfo)
        topStatusRow.addView(statusBadgeBox)

        status = TextView(this).apply {
            text = "Status: Belum terhubung"
            textSize = 12.5f
            setTextColor(colTextSecondary)
            setPadding(0, dp(8), 0, 0)
        }

        connectionInfoBox.addView(topStatusRow)
        connectionInfoBox.addView(status)

        connectionPanel.addView(
            connectionInfoBox,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            }
        )

        // Baris Tombol Aksi Bluetooth (Berdampingan / Grid 2 Kolom agar ringkas & modern)
        val btActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        // Tombol Scan Device
        searchButton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rippleRounded(colSurfaceElevated, 14, colAccentBlue, 1)
            isClickable = true
            isFocusable = true
            setPadding(dp(10), dp(4), dp(10), dp(4))
            minimumHeight = dp(50)

            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_bluetooth)
                setColorFilter(colAccentCyan)
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            })
            addView(Space(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(6), 1)
            })
            addView(TextView(this@MainActivity).apply {
                text = "SCAN BT"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colTextPrimary)
                gravity = Gravity.CENTER
                includeFontPadding = false
            })

            setOnClickListener { findPrinter() }
        }

        // Tombol Connect Printer
        connectButton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rippleRounded(colAccentBlue, 14)
            isClickable = true
            isFocusable = true
            setPadding(dp(10), dp(4), dp(10), dp(4))
            minimumHeight = dp(50)

            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_bluetooth_connect)
                setColorFilter(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            })
            addView(Space(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(6), 1)
            })
            addView(TextView(this@MainActivity).apply {
                text = "CONNECT"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                includeFontPadding = false
            })

            setOnClickListener { connectPrinter() }
        }

        btActionRow.addView(
            searchButton,
            LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(6) }
        )
        btActionRow.addView(
            connectButton,
            LinearLayout.LayoutParams(0, dp(50), 1f).apply { leftMargin = dp(6) }
        )

        connectionPanel.addView(btActionRow)

        layout.addView(
            connectionPanel,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(20)
            }
        )

        // ---------------------------------------------------------------------
        // 3. KARTU RECEIPT PREVIEW (THERMAL PAPER CANVAS)
        // ---------------------------------------------------------------------
        layout.addView(sectionHeader("RECEIPT PREVIEW", "384 PX • 1-BIT MONO"))

        // Frame luar gelap sebagai meja kerja (workspace frame)
        val previewWorkspace = FrameLayout(this).apply {
            background = rounded(colSurface, 20, colBorder, 1)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            elevation = dp(4).toFloat()
        }

        // Kertas Thermal Putih di dalam workspace
        val previewPanel = FrameLayout(this).apply {
            background = rounded(Color.parseColor("#F8FAFC"), 14, Color.parseColor("#CBD5E1"), 1)
            minimumHeight = dp(270)
            setPadding(dp(14), dp(16), dp(14), dp(16))
        }

        // Placeholder Awal yang Lebih Rapi & Modern
        previewPlaceholder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val iconCircle = FrameLayout(this).apply {
            background = rounded(Color.parseColor("#E2E8F0"), 28)
        }
        val paperIcon = TextView(this).apply {
            text = "🧾"
            textSize = 28f
            gravity = Gravity.CENTER
        }
        iconCircle.addView(
            paperIcon,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        previewPlaceholder.addView(
            iconCircle,
            LinearLayout.LayoutParams(dp(60), dp(60)).apply { bottomMargin = dp(12) }
        )

        fun placeholderText(value: String, size: Float, color: Int, bold: Boolean = false): TextView {
            return TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
                if (bold) typeface = Typeface.DEFAULT_BOLD
            }
        }

        previewPlaceholder.addView(
            placeholderText("Belum Ada Resi Dipilih", 15f, Color.parseColor("#1E293B"), true)
        )

        previewPlaceholder.addView(
            placeholderText(
                "Pilih file PDF resi untuk melihat pratinjau cetak thermal",
                12f,
                Color.parseColor("#64748B")
            ).apply {
                setPadding(dp(16), dp(4), dp(16), dp(14))
            }
        )

        // Simulasi baris struk thermal minimal
        val lineWidths = listOf(160, 130, 145, 95)
        for (w in lineWidths) {
            val line = View(this).apply {
                background = rounded(Color.parseColor("#CBD5E1"), 4)
            }
            previewPlaceholder.addView(
                line,
                LinearLayout.LayoutParams(dp(w), dp(6)).apply { topMargin = dp(7) }
            )
        }

        previewPanel.addView(
            previewPlaceholder,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )

        // Gambar hasil PDF yang sudah diproses
        pdfPreview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
        }

        previewPanel.addView(
            pdfPreview,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )

        previewWorkspace.addView(
            previewPanel,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        )

        layout.addView(
            previewWorkspace,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(20)
            }
        )

        // ---------------------------------------------------------------------
        // 4. TOMBOL AKSI UTAMA (SELECT PDF & PRINT RECEIPT)
        // ---------------------------------------------------------------------
        // Tombol Pilih PDF
        selectPdfButton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rippleRounded(colSurfaceElevated, 16, colAccentCyan, 1)
            isClickable = true
            isFocusable = true
            setPadding(dp(16), dp(4), dp(16), dp(4))
            minimumHeight = dp(56)

            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_pdf)
                setColorFilter(colAccentCyan)
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            })
            addView(Space(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(10), 1)
            })
            addView(TextView(this@MainActivity).apply {
                text = "SELECT RECEIPT PDF"
                textSize = 14f
                letterSpacing = 0.04f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colTextPrimary)
                gravity = Gravity.CENTER
                includeFontPadding = false
            })

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
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56))
        )

        // Tombol Cetak Utama (Gradient Emerald Accent)
        printButton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rippleGradient(Color.parseColor("#059669"), Color.parseColor("#10B981"), 16)
            elevation = dp(6).toFloat()
            isClickable = true
            isFocusable = true
            setPadding(dp(16), dp(4), dp(16), dp(4))
            minimumHeight = dp(60)

            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_printer)
                setColorFilter(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            })
            addView(Space(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(10), 1)
            })
            addView(TextView(this@MainActivity).apply {
                text = "PRINT THERMAL RECEIPT"
                textSize = 15f
                letterSpacing = 0.04f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                includeFontPadding = false
            })

            setOnClickListener { printReceipt() }
        }

        layout.addView(
            printButton,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(60)).apply {
                topMargin = dp(12)
                bottomMargin = dp(22)
            }
        )

        // ---------------------------------------------------------------------
        // 5. FOOTER
        // ---------------------------------------------------------------------
        val footer = TextView(this).apply {
            text = "JZ RECEIPT PRINTER  •  ESC/POS THERMAL ENGINE"
            textSize = 10f
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            setTextColor(colTextMuted)
        }
        layout.addView(footer)

        // SCROLL KONTEN
        val scrollView = ScrollView(this).apply {
            setFillViewport(true)
            setBackgroundColor(colBgPrimary)
            isVerticalScrollBarEnabled = false
            addView(layout)
        }

        headerLine.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(1)
        )

        // SUSUN ROOT LAYOUT
        rootLayout.addView(header)
        rootLayout.addView(headerLine)
        rootLayout.addView(
            scrollView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(rootLayout)

        // CEK PRINTER USB SAAT APLIKASI DIBUKA
        scanUsbDevices()
    }

    // =========================================================================
    // BLUETOOTH & PERMISSION LOGIC (TETAP UTUH)
    // =========================================================================
    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
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
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_BT
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BT &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            findPrinter()
        }
    }

    private fun isUsbPrinterConnected(): Boolean {
        return usbConnection != null && usbDevice != null
    }

    private fun updateBluetoothButtons() {
        val enabled = !isUsbPrinterConnected()
        searchButton.isEnabled = enabled
        connectButton.isEnabled = enabled
        searchButton.alpha = if (enabled) 1f else 0.4f
        connectButton.alpha = if (enabled) 1f else 0.4f
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
                updateConnectionVisualState("DISCONNECTED")
                return
            }

            discoveredDevices.clear()
            status.text = "Memindai perangkat Bluetooth..."
            deviceInfo.text = "Mencari printer di sekitar..."
            updateConnectionVisualState("CONNECTING")

            if (bt.isDiscovering) {
                bt.cancelDiscovery()
            }

            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }

            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(discoveryReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(discoveryReceiver, filter)
            }

            receiverRegistered = true

            val started = bt.startDiscovery()
            if (!started) {
                finishBluetoothScan()
                status.text = "Gagal memulai pemindaian"
                updateConnectionVisualState("DISCONNECTED")
            }
        } catch (e: Exception) {
            status.text = "Error scan: ${e.message}"
            updateConnectionVisualState("DISCONNECTED")
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
                deviceInfo.text = "BT: ${selectedDevice.name ?: "Unknown"}"
                status.text = "Perangkat dipilih • Tekan CONNECT"
                updateConnectionVisualState("CONNECTING")
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
            updateConnectionVisualState("DISCONNECTED")
            return
        }

        updateConnectionVisualState("DISCONNECTED")
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

        status.text = "Menghubungkan ke ${printer?.name ?: "Printer"}..."
        updateConnectionVisualState("CONNECTING")

        Thread {
            try {
                socket?.close()
                val newSocket = printer!!.createRfcommSocketToServiceRecord(sppUUID)
                newSocket.connect()
                socket = newSocket

                runOnUiThread {
                    deviceInfo.text = "BT: ${printer?.name ?: "Bluetooth Printer"}"
                    status.text = "Terhubung via Bluetooth • Siap cetak"
                    updateConnectionVisualState("CONNECTED")
                    printButton.isEnabled = true
                    showPrinterConnectedNotification(
                        printer?.name ?: "Bluetooth Printer",
                        "Bluetooth"
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Gagal koneksi: ${e.message}"
                    updateConnectionVisualState("DISCONNECTED")
                    printButton.isEnabled = false
                }
            }
        }.start()
    }

    // =========================================================================
    // FUNGSI CETAK ESC/POS (TETAP UTUH)
    // =========================================================================
    private fun printReceipt() {
        val bitmap = processedBitmap
        if (bitmap == null) {
            status.text = "Pilih PDF resi terlebih dahulu"
            Toast.makeText(this, "Pilih PDF resi terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        val printBitmap = addWatermark(bitmap, APP_WATERMARK)
        val useUsb = isUsbPrinterConnected()
        val activeSocket = socket

        if (!useUsb && (activeSocket == null || !activeSocket.isConnected)) {
            status.text = "Printer belum terhubung"
            Toast.makeText(this, "Printer belum terhubung", Toast.LENGTH_SHORT).show()
            return
        }

        status.text = if (useUsb) "Mencetak melalui USB..." else "Mencetak melalui Bluetooth..."

        Thread {
            try {
                val bluetoothOutput = if (useUsb) null else activeSocket!!.outputStream

                val sendData: (ByteArray) -> Unit = { data ->
                    val success = if (useUsb) {
                        sendUsbData(data)
                    } else {
                        try {
                            bluetoothOutput!!.write(data)
                            bluetoothOutput.flush()
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }

                    if (!success) {
                        throw RuntimeException(
                            if (useUsb) "Koneksi USB terputus" else "Koneksi Bluetooth gagal"
                        )
                    }
                }

                sendData(byteArrayOf(0x1B, 0x40, 0x1B, 0x61, 0x01))

                val width = printBitmap.width
                val height = printBitmap.height
                val widthBytes = width / 8

                var y = 0
                while (y < height) {
                    val chunkHeight = minOf(16, height - y)
                    sendData(
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
                            val r = Color.red(pixel)
                            val g = Color.green(pixel)
                            val b = Color.blue(pixel)
                            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

                            if (gray < 128) {
                                val byteIndex = row * widthBytes + x / 8
                                val bit = 7 - (x % 8)
                                imageData[byteIndex] = (imageData[byteIndex].toInt() or (1 shl bit)).toByte()
                            }
                        }
                    }

                    sendData(imageData)
                    Thread.sleep(10)
                    y += chunkHeight
                }

                sendData(byteArrayOf(0x1B, 0x64, 0x04, 0x1D, 0x56, 0x41, 0x03))

                runOnUiThread {
                    status.text = if (useUsb) {
                        "Resi berhasil dicetak melalui USB!"
                    } else {
                        "Resi berhasil dicetak melalui Bluetooth!"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Gagal mencetak: ${e.message}"
                    updateConnectionVisualState("DISCONNECTED")
                }
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (System.currentTimeMillis() - backPressedTime < 2000) {
            finishAndRemoveTask()
        } else {
            backPressedTime = System.currentTimeMillis()
            Toast.makeText(this, "Ketuk sekali lagi untuk keluar", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }

        disconnectUsbPrinter()

        if (usbPermissionReceiverRegistered) {
            try {
                unregisterReceiver(usbPermissionReceiver)
            } catch (_: Exception) {
            }
            usbPermissionReceiverRegistered = false
        }

        if (usbDeviceReceiverRegistered) {
            try {
                unregisterReceiver(usbDeviceReceiver)
            } catch (_: Exception) {
            }
            usbDeviceReceiverRegistered = false
        }

        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PDF && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            renderPdf(uri)
        }
    }

    // =========================================================================
    // RENDER & IMAGE PROCESSING PDF RESI (TETAP UTUH)
    // =========================================================================
    private fun renderPdf(uri: Uri) {
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
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

                        val result = processReceipt(sourceBitmap)
                        processedBitmap = result

                        pdfPreview.setImageBitmap(result)
                        previewPlaceholder.visibility = View.GONE
                        pdfPreview.visibility = View.VISIBLE

                        if (::resolutionBadge.isInitialized) {
                            resolutionBadge.text = "${result.width} x ${result.height} PX"
                        }

                        status.text = "Resi siap dicetak (${result.width} x ${result.height} px)"
                    }
                }
            } ?: throw Exception("Tidak dapat membuka file PDF")
        } catch (e: Exception) {
            status.text = "Gagal memproses PDF: ${e.message}"
        }
    }

    private fun processReceipt(source: Bitmap): Bitmap {
        val cropX = 0
        val cropY = 35
        val cropWidth = source.width
        val cropHeight = minOf(450, source.height - cropY)

        require(cropHeight > 0) {
            "Ukuran PDF terlalu kecil untuk crop"
        }

        val cropped = Bitmap.createBitmap(source, cropX, cropY, cropWidth, cropHeight)

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

        val trimmed = Bitmap.createBitmap(
            cropped,
            minX,
            minY,
            maxX - minX + 1,
            maxY - minY + 1
        )

        val rotated = rotateBitmap(trimmed, -90f)
        status.text = "Trim: ${trimmed.width}x${trimmed.height} | Rotasi: ${rotated.width}x${rotated.height}"

        val newHeight = (rotated.height * 384f / rotated.width).toInt().coerceAtLeast(1)
        val resized = Bitmap.createScaledBitmap(rotated, 384, newHeight, true)

        val binary = Bitmap.createBitmap(384, newHeight, Bitmap.Config.ARGB_8888)
        for (y in 0 until newHeight) {
            for (x in 0 until 384) {
                val pixel = resized.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                val value = if (gray < 255 * 0.65) 0 else 255
                binary.setPixel(x, y, Color.rgb(value, value, value))
            }
        }

        val finalRotation = rotateBitmap(binary, 180f)

        val result = Bitmap.createBitmap(384, finalRotation.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val paint = Paint()
        canvas.drawBitmap(finalRotation, 0f, 0f, paint)

        val borderPaint = Paint().apply {
            color = Color.BLACK
        }

        canvas.drawRect(0f, 0f, 2f, result.height.toFloat(), borderPaint)
        canvas.drawRect(382f, 0f, 384f, result.height.toFloat(), borderPaint)

        return result
    }

    private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix().apply {
            postRotate(degrees)
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun addWatermark(source: Bitmap, watermark: String): Bitmap {
        val result = Bitmap.createBitmap(
            source.width,
            source.height + 55,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, 0f, 0f, null)

        if (watermark.isNotBlank()) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 26f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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
