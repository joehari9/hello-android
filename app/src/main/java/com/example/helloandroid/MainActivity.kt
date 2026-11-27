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
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
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

    // UI REFERENCES GLOBAL
    private lateinit var status: TextView
    private lateinit var deviceInfo: TextView
    private lateinit var statusDot: View
    private lateinit var statusBadgeText: TextView
    private lateinit var statusBadgeBox: LinearLayout
    private lateinit var connectionInfoBox: LinearLayout

    private lateinit var connectButton: LinearLayout
    private lateinit var searchButton: LinearLayout

    // CONTAINER SUB-HALAMAN DINAMIS
    private lateinit var contentContainer: FrameLayout
    private var activeSubPage: String = "HOME" // "HOME", "PRINT_RECEIPT", "MANUAL_RECEIPT", "PRINT_IMAGE", "PRINT_QR_BARCODE"
    private var printReceiptController: PrintReceiptView? = null
    private var manualReceiptController: ManualReceiptView? = null
    private var printImageController: PrintImageView? = null
    private var printQrBarcodeController: PrintQrBarcodeView? = null
    internal var contentContainerWidth: Int = 0

    private var backPressedTime = 0L

    // THEME PALETTE (MODERN DARK INDUSTRIAL STUDIO)
    internal val colBgPrimary = Color.parseColor("#0B1120")
    internal val colSurface = Color.parseColor("#131C31")
    internal val colSurfaceElevated = Color.parseColor("#1A2642")
    internal val colBorder = Color.parseColor("#263554")
    internal val colAccentBlue = Color.parseColor("#2563EB")
    internal val colAccentCyan = Color.parseColor("#0EA5E9")
    internal val colSuccess = Color.parseColor("#10B981")
    internal val colSuccessBg = Color.parseColor("#064E3B")
    internal val colWarning = Color.parseColor("#F59E0B")
    internal val colWarningBg = Color.parseColor("#451A03")
    internal val colTextPrimary = Color.parseColor("#F8FAFC")
    internal val colTextSecondary = Color.parseColor("#94A3B8")
    internal val colTextMuted = Color.parseColor("#64748B")

    companion object {
        private const val REQUEST_BT = 100
        internal const val REQUEST_PDF = 200
        internal const val REQUEST_IMAGE = 250
        internal const val APP_WATERMARK = "Generate by: JZ Receipt Printer"
        private const val ACTION_USB_PERMISSION = "com.example.helloandroid.USB_PERMISSION"
        private const val REQUEST_USB = 300
        private const val CHANNEL_ID = "printer_connection"
        private const val REQUEST_NOTIFICATION = 400
    }

    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private var receiverRegistered = false

    internal fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    internal fun rounded(
        color: Int,
        radiusDp: Int,
        strokeColor: Int = Color.TRANSPARENT,
        strokeWidthDp: Int = 0
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeWidthDp > 0 && strokeColor != Color.TRANSPARENT) {
                setStroke(dp(strokeWidthDp), strokeColor)
            }
        }
    }

    internal fun gradientRounded(startColor: Int, endColor: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(startColor, endColor)
        ).apply {
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    internal fun rippleRounded(
        color: Int,
        radiusDp: Int,
        strokeColor: Int = Color.TRANSPARENT,
        strokeWidthDp: Int = 0
    ): RippleDrawable {
        val rippleColor = ColorStateList.valueOf(Color.argb(55, 255, 255, 255))
        return RippleDrawable(
            rippleColor,
            rounded(color, radiusDp, strokeColor, strokeWidthDp),
            rounded(Color.WHITE, radiusDp)
        )
    }

    internal fun rippleGradient(startColor: Int, endColor: Int, radiusDp: Int): RippleDrawable {
        val rippleColor = ColorStateList.valueOf(Color.argb(65, 255, 255, 255))
        return RippleDrawable(
            rippleColor,
            gradientRounded(startColor, endColor, radiusDp),
            rounded(Color.WHITE, radiusDp)
        )
    }

    internal fun updateStatusText(message: String) {
        if (::status.isInitialized) {
            status.text = message
        }
    }

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

    // =========================================================================
    // BROADCAST RECEIVERS (BLUETOOTH & USB)
    // =========================================================================
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

    private val usbDeviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> scanUsbDevices()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    disconnectUsbPrinter()
                    scanUsbDevices()
                }
            }
        }
    }

    // =========================================================================
    // USB PRINTER LOGIC
    // =========================================================================
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

    private fun disconnectUsbPrinter() {
        try {
            if (usbConnection != null) {
                usbInterface?.let {
                    usbConnection?.releaseInterface(it)
                }
                usbConnection?.close()
            }
        } catch (_: Exception) {
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
        updateBluetoothButtons()
    }

    // =========================================================================
    // NOTIFICATION HELPER
    // =========================================================================
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
    // ON CREATE (GLOBAL HEADER + CONNECTION CARD + DYNAMIC SUB-PAGE CONTAINER)
    // =========================================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = colBgPrimary
        window.navigationBarColor = colBgPrimary

        createNotificationChannel()
        requestNotificationPermission()

        val usbPermissionFilter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(usbPermissionReceiver, usbPermissionFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbPermissionReceiver, usbPermissionFilter)
        }

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
        // 1. LOGO & NAVBAR (TETAP DI SEMUA HALAMAN)
        // ---------------------------------------------------------------------
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            setBackgroundColor(colBgPrimary)
        }

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

        header.addView(logoContainer, LinearLayout.LayoutParams(dp(44), dp(44)))
        header.addView(headerTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val headerLine = View(this).apply {
            setBackgroundColor(colBorder)
        }

        // ---------------------------------------------------------------------
        // 2. BAGIAN ATAS KONTEN: CONNECTION CARD (TETAP DI SEMUA HALAMAN)
        // ---------------------------------------------------------------------
        val topFixedSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(10))
            setBackgroundColor(colBgPrimary)
        }

        topFixedSection.addView(
            TextView(this).apply {
                text = "HARDWARE CONNECTION"
                textSize = 12f
                letterSpacing = 0.08f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colTextSecondary)
                setPadding(dp(2), 0, dp(2), dp(10))
            }
        )

        val connectionPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(colSurface, 20, colBorder, 1)
            elevation = dp(4).toFloat()
        }

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

        val btActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

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

        topFixedSection.addView(
            connectionPanel,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        // ---------------------------------------------------------------------
        // 3. AREA DINAMIS DI BAWAH CONNECTION CARD (GRID MENU / SUB-MENU)
        // ---------------------------------------------------------------------
        contentContainer = object : FrameLayout(this) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                if (w > 0) {
                    contentContainerWidth = w
                }
            }
        }.apply {
            setBackgroundColor(colBgPrimary)
        }

        // Bungkus topFixedSection + contentContainer dalam satu LinearLayout di ScrollView
        val mainScrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colBgPrimary)
        }
        mainScrollContent.addView(
            topFixedSection,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        mainScrollContent.addView(
            contentContainer,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        val bodyScrollView = ScrollView(this).apply {
            setFillViewport(true)
            setBackgroundColor(colBgPrimary)
            isVerticalScrollBarEnabled = false
            addView(
                mainScrollContent,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        headerLine.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(1)
        )

        // ---------------------------------------------------------------------
        // 4. FIXED BOTTOM NAVIGATION BAR (ABOUT / PRINTER / GUIDE / GITHUB)
        // ---------------------------------------------------------------------
        val bottomDivider = View(this).apply {
            setBackgroundColor(colBorder)
        }

        val bottomNavBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(8))
            setBackgroundColor(colSurface)
            elevation = dp(8).toFloat()
        }

        val bottomTabs = mutableListOf<Pair<LinearLayout, TextView>>()
        var selectedBottomIndex = 0

        fun refreshBottomNavSelection() {
            bottomTabs.forEachIndexed { idx, (tabContainer, tabLabel) ->
                val isSelected = (idx == selectedBottomIndex)
                if (isSelected) {
                    tabContainer.background = rounded(colSurfaceElevated, 12, colAccentCyan, 1)
                    tabLabel.setTextColor(colAccentCyan)
                } else {
                    tabContainer.background = rippleRounded(Color.TRANSPARENT, 12)
                    tabLabel.setTextColor(colTextSecondary)
                }
            }
        }

        val navDefinitions = listOf(
            Triple("⌂", "Home", 0),
            Triple("ⓘ", "About", 1),
            Triple("🖨", "Printer", 2),
            Triple("❔", "Guide", 3)
        )

        navDefinitions.forEach { (glyph, labelText, index) ->
            val tabItem = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(6), dp(4), dp(6))
                background = rippleRounded(Color.TRANSPARENT, 12)
                isClickable = true
                isFocusable = true
            }

            val iconView = TextView(this@MainActivity).apply {
                text = glyph
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(colTextPrimary)
                includeFontPadding = false
            }

            val titleView = TextView(this@MainActivity).apply {
                text = labelText
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(colTextSecondary)
                setPadding(0, dp(2), 0, 0)
                includeFontPadding = false
            }

            tabItem.addView(iconView)
            tabItem.addView(titleView)

            tabItem.setOnClickListener {
                selectedBottomIndex = index
                refreshBottomNavSelection()

                when (index) {
                    0 -> showHomeMenu()
                    1 -> AboutDialog.show(this@MainActivity)
                    2 -> {
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
                    3 -> HelpDialog.show(this@MainActivity)
                }
            }

            bottomTabs.add(tabItem to titleView)
            bottomNavBar.addView(
                tabItem,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = dp(2)
                    rightMargin = dp(2)
                }
            )
        }

        refreshBottomNavSelection()

        rootLayout.addView(header)
        rootLayout.addView(headerLine)
        rootLayout.addView(
            bodyScrollView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        rootLayout.addView(
            bottomDivider,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        )
        rootLayout.addView(
            bottomNavBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        setContentView(rootLayout)

        // Tampilkan halaman utama (Grid 4 Menu)
        showHomeMenu()

        // Cek printer USB saat aplikasi dibuka
        scanUsbDevices()
    }

    // =========================================================================
    // HALAMAN UTAMA: GRID 4 TOMBOL MENU BERDAMPINGAN
    // =========================================================================
    internal fun showHomeMenu() {
        activeSubPage = "HOME"
        contentContainer.removeAllViews()

        val homeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(24))
            setBackgroundColor(colBgPrimary)
        }

        homeLayout.addView(
            TextView(this).apply {
                text = "QUICK MENU"
                textSize = 12f
                letterSpacing = 0.08f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colTextSecondary)
                setPadding(dp(2), 0, dp(2), dp(10))
            }
        )

        fun buildMenuTile(
            title: String,
            subtitle: String,
            iconRes: Int?,
            fallbackEmoji: String,
            accentColor: Int,
            isComingSoon: Boolean = false,
            onClick: () -> Unit
        ): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.START
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = rippleRounded(
                    colSurface,
                    18,
                    if (isComingSoon) colBorder else accentColor,
                    1
                )
                isClickable = true
                isFocusable = true
                alpha = if (isComingSoon) 0.65f else 1f
                elevation = dp(3).toFloat()

                val iconBadge = FrameLayout(this@MainActivity).apply {
                    background = rounded(colSurfaceElevated, 12, colBorder, 1)
                }

                if (iconRes != null) {
                    iconBadge.addView(
                        ImageView(this@MainActivity).apply {
                            setImageResource(iconRes)
                            setColorFilter(accentColor)
                            setPadding(dp(9), dp(9), dp(9), dp(9))
                        },
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                } else {
                    iconBadge.addView(
                        TextView(this@MainActivity).apply {
                            text = fallbackEmoji
                            textSize = 20f
                            gravity = Gravity.CENTER
                        },
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    )
                }

                addView(iconBadge, LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                    bottomMargin = dp(12)
                })

                addView(TextView(this@MainActivity).apply {
                    text = title
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(colTextPrimary)
                })

                addView(TextView(this@MainActivity).apply {
                    text = subtitle
                    textSize = 11f
                    setTextColor(if (isComingSoon) colWarning else colTextSecondary)
                    setPadding(0, dp(3), 0, 0)
                })

                setOnClickListener { onClick() }
            }
        }

        // Baris 1: 1. Manual Struk | 2. Print Receipt
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val tileManualStruk = buildMenuTile(
            title = "Manual Struk",
            subtitle = "Buat & cetak teks",
            iconRes = null,
            fallbackEmoji = "🧾",
            accentColor = colAccentCyan,
            isComingSoon = false
        ) {
            openManualReceiptSubPage()
        }

        val tilePrintReceipt = buildMenuTile(
            title = "Print Receipt",
            subtitle = "PDF resi 58mm / 80mm",
            iconRes = R.drawable.ic_pdf,
            fallbackEmoji = "📄",
            accentColor = colSuccess,
            isComingSoon = false
        ) {
            openPrintReceiptSubPage()
        }

        row1.addView(tileManualStruk, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(6)
        })
        row1.addView(tilePrintReceipt, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(6)
        })

        // Baris 2: 3. Print Image / Logo | 4. Other (Soon)
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val tilePrintImage = buildMenuTile(
            title = "Print Image/Logo",
            subtitle = "Gambar & logo toko",
            iconRes = R.drawable.ic_printer,
            fallbackEmoji = "🖼",
            accentColor = colAccentBlue,
            isComingSoon = false
        ) {
            openPrintImageSubPage()
        }

        val tileOtherSoon = buildMenuTile(
            title = "QR / Barcode",
            subtitle = "Auto 58mm / 80mm",
            iconRes = null,
            fallbackEmoji = "▦",
            accentColor = colWarning,
            isComingSoon = false
        ) {
            openPrintQrBarcodeSubPage()
        }

        row2.addView(tilePrintImage, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(6)
        })
        row2.addView(tileOtherSoon, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(6)
        })

        homeLayout.addView(row1, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(12)
        })
        homeLayout.addView(row2, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(20)
        })

        homeLayout.addView(
            TextView(this).apply {
                text = "JZ RECEIPT PRINTER  •  ESC/POS THERMAL ENGINE"
                textSize = 10f
                letterSpacing = 0.08f
                gravity = Gravity.CENTER
                setTextColor(colTextMuted)
            }
        )

        contentContainer.addView(
            homeLayout,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        )
    }

    // =========================================================================
    // NAVIGASI KE SUB-HALAMAN MANUAL STRUK (FILE TERPISAH: ManualReceiptView.kt)
    // =========================================================================
    private fun openManualReceiptSubPage() {
        activeSubPage = "MANUAL_RECEIPT"
        contentContainer.removeAllViews()

        if (manualReceiptController == null) {
            manualReceiptController = ManualReceiptView(
                activity = this,
                onBackToHome = { showHomeMenu() },
                onPrintBitmap = { bitmapToPrint ->
                    printProcessedBitmap(bitmapToPrint)
                }
            )
        }

        contentContainer.addView(
            manualReceiptController!!.buildView(),
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        )
    }

    // =========================================================================
    // NAVIGASI KE SUB-HALAMAN QR CODE / BARCODE (FILE TERPISAH: PrintQrBarcodeView.kt)
    // =========================================================================
    private fun openPrintQrBarcodeSubPage() {
        activeSubPage = "PRINT_QR_BARCODE"
        contentContainer.removeAllViews()

        if (printQrBarcodeController == null) {
            printQrBarcodeController = PrintQrBarcodeView(
                activity = this,
                onBackToHome = { showHomeMenu() },
                onPrintQrOrBarcodeBitmap = { bitmapToPrint ->
                    printProcessedBitmap(bitmapToPrint)
                }
            )
        }

        contentContainer.addView(
            printQrBarcodeController!!.buildView(),
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        )
    }

    // =========================================================================
    // NAVIGASI KE SUB-HALAMAN PRINT IMAGE / LOGO (FILE TERPISAH: PrintImageView.kt)
    // =========================================================================
    private fun openPrintImageSubPage() {
        activeSubPage = "PRINT_IMAGE"
        contentContainer.removeAllViews()

        if (printImageController == null) {
            printImageController = PrintImageView(
                activity = this,
                onBackToHome = { showHomeMenu() },
                onPickImage = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQUEST_IMAGE)
                },
                onPrintImageBitmap = { bitmapToPrint ->
                    printImageAutoWidth(bitmapToPrint)
                }
            )
        }

        contentContainer.addView(
            printImageController!!.buildView(),
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        )
    }

    // =========================================================================
    // NAVIGASI KE SUB-HALAMAN PRINT RECEIPT (FILE TERPISAH: PrintReceiptView.kt)
    // =========================================================================
    private fun openPrintReceiptSubPage() {
        activeSubPage = "PRINT_RECEIPT"
        contentContainer.removeAllViews()

        if (printReceiptController == null) {
            printReceiptController = PrintReceiptView(
                activity = this,
                onBackToHome = { showHomeMenu() },
                onPickPdf = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/pdf"
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQUEST_PDF)
                },
                onPrintBitmap = { bitmapToPrint ->
                    printProcessedBitmap(bitmapToPrint)
                }
            )
        }

        contentContainer.addView(
            printReceiptController!!.buildView(),
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        )
    }

    // =========================================================================
    // BLUETOOTH & PERMISSION LOGIC
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

    internal fun getConnectedPrinterNameForAutoWidth(): String {
        if (isUsbPrinterConnected()) {
            return usbDevice?.productName ?: ""
        }
        if (socket?.isConnected == true) {
            return printer?.name ?: ""
        }
        return ""
    }

    internal fun isUsbPrinterConnected(): Boolean {
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
                    showPrinterConnectedNotification(
                        printer?.name ?: "Bluetooth Printer",
                        "Bluetooth"
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Gagal koneksi: ${e.message}"
                    updateConnectionVisualState("DISCONNECTED")
                }
            }
        }.start()
    }

    // =========================================================================
    // FUNGSI CETAK ESC/POS (DIPANGGIL DARI SUB-HALAMAN)
    // =========================================================================
    internal fun fitBitmapToThermalWidth(
        source: Bitmap,
        targetDotWidth: Int = resolveTargetDotWidthForBitmap(),
        horizontalMarginPx: Int = 16
    ): Bitmap {
        val safeTargetWidth = targetDotWidth.coerceIn(240, 832)
        val innerMaxWidth = (safeTargetWidth - (horizontalMarginPx * 2)).coerceAtLeast(120)

        val srcW = source.width.coerceAtLeast(1)
        val srcH = source.height.coerceAtLeast(1)
        val scale = innerMaxWidth.toFloat() / srcW.toFloat()

        val scaledW = (srcW * scale).toInt().coerceAtLeast(1).coerceAtMost(innerMaxWidth)
        val scaledH = (srcH * scale).toInt().coerceAtLeast(1)

        val scaledBitmap = Bitmap.createScaledBitmap(source, scaledW, scaledH, true)
        val outBitmap = Bitmap.createBitmap(safeTargetWidth, scaledH + (horizontalMarginPx * 2), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBitmap)
        canvas.drawColor(Color.WHITE)

        val left = ((safeTargetWidth - scaledW) / 2f).coerceAtLeast(0f)
        canvas.drawBitmap(scaledBitmap, left, horizontalMarginPx.toFloat(), null)

        if (scaledBitmap != source) {
            scaledBitmap.recycle()
        }
        return outBitmap
    }

    internal fun resolveTargetDotWidthForBitmap(): Int {
        val name = getConnectedPrinterNameForAutoWidth().lowercase(java.util.Locale.getDefault())
        return if (name.contains("80") || name.contains("576") || name.contains("72mm")) {
            576
        } else {
            384
        }
    }

    internal fun printImageAutoWidth(bitmap: Bitmap, horizontalMarginPx: Int = 16) {
        val fitted = fitBitmapToThermalWidth(bitmap, resolveTargetDotWidthForBitmap(), horizontalMarginPx)
        printProcessedBitmap(fitted)
    }

    internal fun printProcessedBitmap(bitmap: Bitmap) {
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

                sendData(byteArrayOf(0x1B, 0x40, 0x1B, 0x61, 0x00))

                val width = printBitmap.width
                val height = printBitmap.height
                val widthBytes = (width + 7) / 8

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

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (activeSubPage != "HOME") {
            showHomeMenu()
            return
        }

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
            if (activeSubPage == "PRINT_RECEIPT") {
                printReceiptController?.handleSelectedPdf(uri)
            }
        } else if (requestCode == REQUEST_IMAGE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            if (activeSubPage == "PRINT_IMAGE") {
                printImageController?.handleSelectedImage(uri)
            }
        }
    }
}
