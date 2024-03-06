package com.rustui.hid_android

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.annotation.NonNull
import com.google.gson.Gson

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import kotlin.experimental.and

private const val REQUEST_GET_REPORT = 0x01;
private const val REQUEST_SET_REPORT = 0x09;
private const val REPORT_TYPE_INPUT = 0x0100;
private const val REPORT_TYPE_OUTPUT = 0x0200;
private const val REPORT_TYPE_FEATURE = 0x0300;

/** HidAndroidPlugin */
class HidAndroidPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel

    private lateinit var context: Context
    private lateinit var usbManager: UsbManager
    private val gson = Gson()
    private var connection: UsbDeviceConnection? = null
    private var device: UsbDevice? = null

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "hid_android")
        channel.setMethodCallHandler(this)

        context = flutterPluginBinding.applicationContext
        usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "getDeviceList" -> {
                val devices: MutableList<String> = mutableListOf()
                for (device in usbManager.deviceList.values) {
                    try {
                        val json = gson.toJson(
                            HidDevice(
                                device.vendorId,
                                device.productId,
                                device.serialNumber ?: "",
                                device.productName ?: "",
                                device.deviceName
                            )
                        )
                        devices.add(json)
                    } catch (e: Exception) {
                        val permissionIntent =
                            PendingIntent.getBroadcast(
                                context,
                                0,
                                Intent("ACTION_USB_PERMISSION"),
                                0
                            )
                        usbManager.requestPermission(device, permissionIntent)
                    }
                }
                result.success(devices)
            }
            "open" -> {
                device = usbManager.deviceList[call.argument("deviceName")]!!
                connection = usbManager.openDevice(device)
                
                result.success( true )
            }
            "read" -> {
                if (connection != null) {
                    val length: Int = call.argument("length")!!
                    val duration: Int = call.argument("duration")!!
                    val pair = getReadIndices(device!!)
                    if (pair == null) {
                        result.error("error", "error", "error")
                    } else {
                        val interfaceIndex = pair.first;
                        val endpointIndex = pair.second;
                        if (!connection!!.claimInterface(device!!.getInterface(interfaceIndex!!), true)) {
                            result.error("error", "error", "error")
                        } else {
                            Thread {
                                kotlin.run {
                                    val array = ByteArray(length)
                                    connection!!.bulkTransfer(
                                        device!!.getInterface(interfaceIndex!!).getEndpoint(endpointIndex!!),
                                        array,
                                        length,
                                        duration
                                    )
                                    result.success(array.map { it.toUByte().toInt() })
                                }
                            }.start()
                        }
                    }
                } else {
                    result.error("error", "error", "error")
                }
            }
            "write" -> {
                if (connection != null) {
                    val bytes: ByteArray = call.argument("bytes")!!
                    val pair = getWriteIndices(device!!)
                    if (pair == null) {
                        result.error("error", "error", "error")
                    } else {
                        val interfaceIndex = pair.first;
                        val endpointIndex = pair.second;
                        if (!connection!!.claimInterface(device!!.getInterface(interfaceIndex!!), true)) {
                            result.error("error", "error", "error")
                        } else {
                            Thread {
                                kotlin.run {
                                    connection!!.bulkTransfer(
                                        device!!.getInterface(interfaceIndex!!).getEndpoint(endpointIndex!!),
                                        bytes,
                                        bytes.size,
                                        1000
                                    )
                                    result.success(0)
                                }
                            }.start()
                        }
                    }
                } else {
                    result.error("error", "error", "error")
                }
            }
            "setFeature" -> {
                if (connection != null) {
                    val bytes: ByteArray = call.argument("bytes")!!
                    val pair = getHIDIndices(device!!)
                    if (pair == null) {
                        result.error("error", "error", "error")
                    } else {
                        val interfaceIndex = pair.first;
                        val endpointIndex = pair.second;
                        if (!connection!!.claimInterface(device!!.getInterface(interfaceIndex!!), true)) {
                            result.error("error", "error", "error")
                        } else {
                            Thread {
                                kotlin.run {
                                    val reportId = bytes.get(0).toInt() and 0xff

                                    connection!!.controlTransfer(
                                        UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_CLASS or UsbConstants.USB_INTERFACE_SUBCLASS_BOOT,
                                        REQUEST_SET_REPORT,
                                        reportId or REPORT_TYPE_OUTPUT, 
                                        interfaceIndex ?: 0,
                                        bytes,
                                        1,
                                        bytes.size - 1,
                                        0
                                    )
                                    result.success(0)
                                }
                            }.start()
                        }
                    }
                } else {
                    result.error("error", "error", "error")
                }
            }
            "getFeature" -> {
                if (connection != null) {
                    val bytes: ByteArray = call.argument("bytes")!!
                    val pair = getHIDIndices(device!!)
                    if (pair == null) {
                        result.error("error", "error", "error")
                    } else {
                        val interfaceIndex = pair.first;
                        val endpointIndex = pair.second;
                        if (!connection!!.claimInterface(device!!.getInterface(interfaceIndex!!), true)) {
                            result.error("error", "error", "error")
                        } else {
                            Thread {
                                kotlin.run {
                                    val reportId = bytes.get(0).toInt() and 0xff

                                    connection!!.controlTransfer(
                                        UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_CLASS or UsbConstants.USB_INTERFACE_SUBCLASS_BOOT,
                                        REQUEST_GET_REPORT,
                                        reportId or REPORT_TYPE_INPUT,
                                        interfaceIndex ?: 0,
                                        bytes,
                                        bytes.size,
                                        0
                                    )
                                    result.success(0)
                                }
                            }.start()
                        }
                    }
                } else {
                    result.error("error", "error", "error")
                }
            }
            "close" -> {
                connection?.close()
                connection = null
                device = null
                result.success(0)
            }
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }
}

fun getReadIndices(device: UsbDevice): Pair<Int, Int>? {
    for (i in 0 until device.interfaceCount) {
        val inter = device.getInterface(i)
        for (j in 0 until inter.endpointCount) {
            val endpoint = inter.getEndpoint(j)
            if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_INT && endpoint.direction == UsbConstants.USB_DIR_IN) {
                return Pair(i, j)
            }
        }
    }
    return null
}

fun getWriteIndices(device: UsbDevice): Pair<Int, Int>? {
    for (i in 0 until device.interfaceCount) {
        val inter = device.getInterface(i)
        for (j in 0 until inter.endpointCount) {
            val endpoint = inter.getEndpoint(j)
            if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_INT && endpoint.direction == UsbConstants.USB_DIR_OUT) {
                return Pair(i, j)
            }
        }
    }
    return null
}

fun getHIDIndices(device: UsbDevice): Pair<Int, Int>? {
    for (i in 0 until device.interfaceCount) {
        val inter = device.getInterface(i)
        if (inter.getInterfaceClass() == UsbConstants.USB_CLASS_HID) {
            for (j in 0 until inter.endpointCount) {
                val endpoint = inter.getEndpoint(j)
                if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_INT && endpoint.direction == UsbConstants.USB_DIR_IN) {
                    return Pair(i, j)
                }
            }
        }
    }
    return null
}