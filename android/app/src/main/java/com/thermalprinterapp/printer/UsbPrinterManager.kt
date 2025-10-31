package com.thermalprinterapp.printer

import android.app.PendingIntent
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.hardware.usb.*
import android.os.Build
import android.util.Base64
import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter
import java.io.ByteArrayOutputStream

class UsbPrinterManager(private val reactContext: ReactApplicationContext) {

    private val TAG = "UsbPrinterManager"
    private val ACTION_USB_PERMISSION = "com.thermalprinterapp.USB_PERMISSION"
    private val usbManager: UsbManager = reactContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbDevice: UsbDevice? = null
    private var usbInterface: UsbInterface? = null
    private var connection: UsbDeviceConnection? = null
    private var outputEndpoint: UsbEndpoint? = null
    private var isReceiverRegistered = false
    private var connectionCheckHandler: android.os.Handler? = null
    private var connectionCheckRunnable: Runnable? = null
    private var reconnectionCheckHandler: android.os.Handler? = null
    private var reconnectionCheckRunnable: Runnable? = null

    // Printer settings
    private val PRINTER_WIDTH_58MM = 384  // 58mm printer (48mm printable)
    private val PRINTER_WIDTH_80MM = 576  // 80mm printer (72mm printable)
    private var printerWidth = PRINTER_WIDTH_58MM

    init {
        registerReceiver()
        reactContext.runOnUiQueueThread {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                scanConnectedPrinters()
            }, 500)
        }
    }

    private fun registerReceiver() {
        try {
            if (!isReceiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(ACTION_USB_PERMISSION)
                    addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                    addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    reactContext.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    reactContext.registerReceiver(usbReceiver, filter)
                }
                isReceiverRegistered = true
                Log.d(TAG, "Receiver registered successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register receiver: ${e.message}")
            e.printStackTrace()
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Broadcast received: ${intent?.action}")
            
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent?.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            
            when (intent?.action) {
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    Log.d(TAG, "Permission result: $granted for device: ${device?.deviceName}")
                    if (granted && device != null) {
                        connectPrinter(device)
                    } else {
                        Log.w(TAG, "USB permission denied")
                        sendEvent("printerDisconnected")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Log.d(TAG, "USB device attached: ${device?.deviceName}")
                    device?.takeIf { isPrinter(it) }?.let { 
                        requestPermission(it) 
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.d(TAG, "USB device detached: ${device?.deviceName}")
                    device?.takeIf { it.deviceId == usbDevice?.deviceId }?.let { 
                        disconnectPrinter()
                        sendEvent("printerDisconnected")
                    }
                }
            }
        }
    }

    private fun scanConnectedPrinters() {
        Log.d(TAG, "Scanning for connected printers...")
        val deviceList = usbManager.deviceList
        Log.d(TAG, "Found ${deviceList.size} USB devices")
        
        for (device in deviceList.values) {
            Log.d(TAG, "Checking device: ${device.deviceName}, Class: ${device.deviceClass}")
            if (isPrinter(device)) {
                Log.d(TAG, "Printer found: ${device.deviceName}")
                requestPermission(device)
                return
            }
        }
        
        if (deviceList.isEmpty()) {
            Log.d(TAG, "No USB devices found")
            sendEvent("printerDisconnected")
        }
        
        // If no printer found, start reconnection monitoring
        if (usbDevice == null) {
            startReconnectionMonitoring()
        }
    }

    private fun isPrinter(device: UsbDevice): Boolean {
        if (device.deviceClass == UsbConstants.USB_CLASS_PRINTER) {
            return true
        }
        
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                return true
            }
        }
        
        return false
    }

    private fun requestPermission(device: UsbDevice) {
        reactContext.runOnUiQueueThread {
            try {
                if (usbManager.hasPermission(device)) {
                    Log.d(TAG, "Permission already granted for ${device.deviceName}")
                    connectPrinter(device)
                } else {
                    Log.d(TAG, "Requesting permission for ${device.deviceName}")
                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_MUTABLE
                    } else {
                        0
                    }
                    val intent = PendingIntent.getBroadcast(
                        reactContext, 
                        0, 
                        Intent(ACTION_USB_PERMISSION), 
                        flags
                    )
                    usbManager.requestPermission(device, intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting permission: ${e.message}")
                e.printStackTrace()
                sendEvent("printerDisconnected")
            }
        }
    }

    private fun connectPrinter(device: UsbDevice) {
        try {
            Log.d(TAG, "Attempting to connect to printer: ${device.deviceName}")
            
            var printerInterface: UsbInterface? = null
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                    printerInterface = iface
                    break
                }
            }
            
            if (printerInterface == null && device.interfaceCount > 0) {
                printerInterface = device.getInterface(0)
            }
            
            if (printerInterface == null) {
                throw Exception("No suitable interface found")
            }
            
            val conn = usbManager.openDevice(device)
            if (conn == null) {
                throw Exception("Failed to open USB device")
            }
            
            if (!conn.claimInterface(printerInterface, true)) {
                throw Exception("Failed to claim interface")
            }
            
            var endpoint: UsbEndpoint? = null
            for (i in 0 until printerInterface.endpointCount) {
                val ep = printerInterface.getEndpoint(i)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && 
                    ep.direction == UsbConstants.USB_DIR_OUT) {
                    endpoint = ep
                    break
                }
            }
            
            if (endpoint == null) {
                throw Exception("No output endpoint found")
            }
            
            connection = conn
            usbInterface = printerInterface
            outputEndpoint = endpoint
            usbDevice = device
            
            Log.d(TAG, "Printer connected successfully")
            sendEvent("printerConnected")
            
            // Start connection monitoring
            startConnectionMonitoring()
            
            // Stop reconnection monitoring if it was running
            stopReconnectionMonitoring()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect printer: ${e.message}")
            e.printStackTrace()
            disconnectPrinter()
            sendEvent("printerDisconnected")
        }
    }

    private fun disconnectPrinter() {
        try {
            // Stop connection monitoring
            stopConnectionMonitoring()
            
            connection?.releaseInterface(usbInterface)
            connection?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting: ${e.message}")
        } finally {
            usbDevice = null
            usbInterface = null
            outputEndpoint = null
            connection = null
            Log.d(TAG, "Printer disconnected")
            sendEvent("printerDisconnected")
            
            // Start reconnection monitoring
            startReconnectionMonitoring()
        }
    }

    private fun sendEvent(eventName: String) {
        try {
            reactContext
                .getJSModule(RCTDeviceEventEmitter::class.java)
                .emit(eventName, null)
            Log.d(TAG, "Event sent: $eventName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send event: ${e.message}")
        }
    }

    private fun sendToPrinter(data: ByteArray) {
        Thread {
            try {
                if (connection == null || outputEndpoint == null) {
                    Log.e(TAG, "Printer not connected")
                    sendEvent("printerDisconnected")
                    return@Thread
                }
                
                val result = connection?.bulkTransfer(outputEndpoint, data, data.size, 3000)
                Log.d(TAG, "Sent ${data.size} bytes, result: $result")
                
                // Check if transfer failed (result < 0 means error)
                if (result != null && result < 0) {
                    Log.e(TAG, "Transfer failed, printer may be disconnected")
                    disconnectPrinter()
                    sendEvent("printerDisconnected")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending to printer: ${e.message}")
                e.printStackTrace()
                disconnectPrinter()
                sendEvent("printerDisconnected")
            }
        }.start()
    }

    // ESC/POS Commands
    private fun initPrinter(): ByteArray = byteArrayOf(0x1B, 0x40) // ESC @
    private fun feedLine(): ByteArray = byteArrayOf(0x0A) // Line feed
    private fun feedLines(lines: Int): ByteArray = byteArrayOf(0x1B, 0x64, lines.toByte()) // ESC d n
    
    fun printText(text: String) {
        val data = text.toByteArray(Charsets.UTF_8) + feedLine()
        sendToPrinter(data)
    }

    fun printImage(base64: String, width: Int = 0) {
        Thread {
            try {
                Log.d(TAG, "Processing image for printing...")
                
                // Decode base64 to bitmap
                val imageBytes = Base64.decode(base64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                
                if (bitmap == null) {
                    Log.e(TAG, "Failed to decode image")
                    return@Thread
                }
                
                Log.d(TAG, "Original image size: ${bitmap.width}x${bitmap.height}")
                
                // Resize and convert to monochrome
                val targetWidth = if (width > 0 && width <= printerWidth) width else printerWidth
                val processedBitmap = processImageForPrinting(bitmap, targetWidth)
                
                // Convert to ESC/POS format
                val imageData = convertBitmapToEscPos(processedBitmap)
                
                // Send to printer
                sendToPrinter(imageData)
                
                Log.d(TAG, "Image sent to printer successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error printing image: ${e.message}")
                e.printStackTrace()
            }
        }.start()
    }

    private fun processImageForPrinting(originalBitmap: Bitmap, targetWidth: Int): Bitmap {
        // Calculate target height maintaining aspect ratio
        val aspectRatio = originalBitmap.height.toFloat() / originalBitmap.width.toFloat()
        val targetHeight = (targetWidth * aspectRatio).toInt()
        
        // Resize bitmap
        val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
        
        // Convert to monochrome (black & white)
        val monoChromeBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        
        for (y in 0 until targetHeight) {
            for (x in 0 until targetWidth) {
                val pixel = resizedBitmap.getPixel(x, y)
                val gray = (Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114).toInt()
                
                // Simple threshold for better quality
                val newPixel = if (gray < 128) Color.BLACK else Color.WHITE
                monoChromeBitmap.setPixel(x, y, newPixel)
            }
        }
        
        return monoChromeBitmap
    }

    private fun convertBitmapToEscPos(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        
        // Calculate bytes per line (each byte represents 8 pixels)
        val bytesPerLine = (width + 7) / 8
        
        val outputStream = ByteArrayOutputStream()
        
        // Center alignment
        outputStream.write(byteArrayOf(0x1B, 0x61, 0x01))
        
        // ESC/POS bitmap command: ESC * m nL nH [data]
        // m = mode (33 = 24-dot double-density)
        val mode = 33.toByte()
        
        // Process image in strips of 24 pixels height
        var y = 0
        while (y < height) {
            val stripHeight = minOf(24, height - y)
            
            // Write command header
            outputStream.write(0x1B) // ESC
            outputStream.write(0x2A) // *
            outputStream.write(mode.toInt())
            outputStream.write(bytesPerLine and 0xFF) // nL
            outputStream.write((bytesPerLine shr 8) and 0xFF) // nH
            
            // Write image data
            for (x in 0 until width) {
                for (stripe in 0 until 3) { // 3 bytes for 24 dots
                    var byte = 0
                    for (bit in 0 until 8) {
                        val pixelY = y + stripe * 8 + bit
                        if (pixelY < height) {
                            val pixel = bitmap.getPixel(x, pixelY)
                            if (pixel == Color.BLACK) {
                                byte = byte or (1 shl (7 - bit))
                            }
                        }
                    }
                    outputStream.write(byte)
                }
            }
            
            // Line feed
            outputStream.write(0x0A)
            
            y += stripHeight
        }
        
        // Reset alignment to left
        outputStream.write(byteArrayOf(0x1B, 0x61, 0x00))
        
        // Feed some lines after image
        outputStream.write(feedLines(2))
        
        return outputStream.toByteArray()
    }

    fun cutPaper() {
        // Full cut: GS V 0
        // Partial cut: GS V 1
        sendToPrinter(byteArrayOf(0x1D, 0x56, 0x00))
    }
    
    fun setPrinterWidth(width: Int) {
        printerWidth = when {
            width <= 384 -> PRINTER_WIDTH_58MM
            else -> PRINTER_WIDTH_80MM
        }
        Log.d(TAG, "Printer width set to: $printerWidth")
    }
    
    fun isConnected(): Boolean {
        return connection != null && usbDevice != null && outputEndpoint != null
    }
    
    private fun startConnectionMonitoring() {
        stopConnectionMonitoring()
        
        connectionCheckHandler = android.os.Handler(android.os.Looper.getMainLooper())
        connectionCheckRunnable = object : Runnable {
            override fun run() {
                checkPrinterConnection()
                connectionCheckHandler?.postDelayed(this, 2000) // Check every 2 seconds
            }
        }
        connectionCheckHandler?.post(connectionCheckRunnable!!)
        Log.d(TAG, "Connection monitoring started")
    }
    
    private fun stopConnectionMonitoring() {
        connectionCheckRunnable?.let {
            connectionCheckHandler?.removeCallbacks(it)
        }
        connectionCheckHandler = null
        connectionCheckRunnable = null
        Log.d(TAG, "Connection monitoring stopped")
    }
    
    private fun checkPrinterConnection() {
        Thread {
            try {
                val device = usbDevice
                if (device == null) {
                    return@Thread
                }
                
                // Check if device is still in the device list
                val deviceStillConnected = usbManager.deviceList.values.any { 
                    it.deviceId == device.deviceId 
                }
                
                if (!deviceStillConnected) {
                    Log.w(TAG, "Device no longer in device list")
                    disconnectPrinter()
                    return@Thread
                }
                
                // Try to check if connection is still valid
                val fileDescriptor = connection?.fileDescriptor
                if (fileDescriptor == null || fileDescriptor == -1) {
                    Log.w(TAG, "Connection file descriptor invalid")
                    disconnectPrinter()
                    return@Thread
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking connection: ${e.message}")
                disconnectPrinter()
            }
        }.start()
    }
    
    private fun startReconnectionMonitoring() {
        stopReconnectionMonitoring()
        
        reconnectionCheckHandler = android.os.Handler(android.os.Looper.getMainLooper())
        reconnectionCheckRunnable = object : Runnable {
            override fun run() {
                checkForPrinterReconnection()
                reconnectionCheckHandler?.postDelayed(this, 3000) // Check every 3 seconds
            }
        }
        reconnectionCheckHandler?.post(reconnectionCheckRunnable!!)
        Log.d(TAG, "Reconnection monitoring started")
    }
    
    private fun stopReconnectionMonitoring() {
        reconnectionCheckRunnable?.let {
            reconnectionCheckHandler?.removeCallbacks(it)
        }
        reconnectionCheckHandler = null
        reconnectionCheckRunnable = null
        Log.d(TAG, "Reconnection monitoring stopped")
    }
    
    private fun checkForPrinterReconnection() {
        Thread {
            try {
                // Only check if we're currently disconnected
                if (usbDevice != null) {
                    return@Thread
                }
                
                Log.d(TAG, "Checking for printer reconnection...")
                
                // Scan for available printers
                val deviceList = usbManager.deviceList
                for (device in deviceList.values) {
                    if (isPrinter(device)) {
                        Log.d(TAG, "Printer detected, attempting to reconnect: ${device.deviceName}")
                        requestPermission(device)
                        return@Thread
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for reconnection: ${e.message}")
            }
        }.start()
    }
    
    fun cleanup() {
        try {
            stopConnectionMonitoring()
            stopReconnectionMonitoring()
            
            if (isReceiverRegistered) {
                reactContext.unregisterReceiver(usbReceiver)
                isReceiverRegistered = false
                Log.d(TAG, "Receiver unregistered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
        disconnectPrinter()
    }
}