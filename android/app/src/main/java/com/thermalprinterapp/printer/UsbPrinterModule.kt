package com.thermalprinterapp.printer

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.Callback

class UsbPrinterModule(reactContext: ReactApplicationContext) : 
    ReactContextBaseJavaModule(reactContext), LifecycleEventListener {

    private var manager: UsbPrinterManager? = null

    init {
        reactContext.addLifecycleEventListener(this)
        manager = UsbPrinterManager(reactContext)
    }

    override fun getName(): String = "UsbPrinter"

    @ReactMethod
    fun printText(text: String) {
        manager?.printText(text)
    }

    @ReactMethod
    fun printImage(base64: String, width: Int) {
        manager?.printImage(base64, width)
    }

    @ReactMethod
    fun cutPaper() {
        manager?.cutPaper()
    }

    @ReactMethod
    fun setPrinterWidth(width: Int) {
        manager?.setPrinterWidth(width)
    }
    
    @ReactMethod
    fun isConnected(callback: Callback) {
        val connected = manager?.isConnected() ?: false
        callback.invoke(connected)
    }

    override fun onHostResume() {
        // Activity resumed
    }

    override fun onHostPause() {
        // Activity paused
    }

    override fun onHostDestroy() {
        manager?.cleanup()
        manager = null
    }

    override fun invalidate() {
        super.invalidate()
        reactApplicationContext.removeLifecycleEventListener(this)
        manager?.cleanup()
        manager = null
    }
}