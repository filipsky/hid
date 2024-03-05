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

/** HidAndroidPlugin */
class HidAndroidPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var channel: MethodChannel

    private lateinit var context: Context
    private lateinit var usbManager: UsbManager
    private val gson = Gson()
    private var connection: UsbDeviceConnection? = null
    private var device: UsbDevice? = null
    private var readInterfaceIndex: Int? = null
    private var readEndpointIndex: Int? = null
    private var writeInterfaceIndex: Int? = null
    private var writeEndpointIndex: Int? = null

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
                var pair = getReadIndices(device!!)!!
                readInterfaceIndex = pair.first
                readEndpointIndex = pair.second
                pair = getWriteIndices(device!!)!!
                writeInterfaceIndex = pair.first
                writeEndpointIndex = pair.second

                val success : Boolean = connection!!.claimInterface(device!!.getInterface(readInterfaceIndex!!), true) &&
                     ((readInterfaceIndex == writeInterfaceIndex) || connection!!.claimInterface(device!!.getInterface(writeInterfaceIndex!!), true))

                result.success( success )
            }
            "read" -> {
                if (connection != null) {
                    val length: Int = call.argument("length")!!
                    val duration: Int = call.argument("duration")!!
                    Thread {
                        kotlin.run {
                            val array = ByteArray(length)
                            connection!!.bulkTransfer(
                                device!!.getInterface(readInterfaceIndex!!).getEndpoint(readEndpointIndex!!),
                                array,
                                length,
                                duration
                            )
                            result.success(array.map { it.toUByte().toInt() })
                        }
                    }.start()
                } else {
                    result.error("error", "error", "error")
                }
            }
            "write" -> {
                if (connection != null) {
                    val bytes: ByteArray = call.argument("bytes")!!
                    Thread {
                        kotlin.run {
                            connection!!.bulkTransfer(
                                device!!.getInterface(writeInterfaceIndex!!).getEndpoint(writeEndpointIndex!!),
                                bytes,
                                bytes.size,
                                1000
                            )
                            result.success(0)
                        }
                    }.start()
                } else {
                    result.error("error", "error", "error")
                }
            }
            "setFeature" -> {
                if (connection != null) {
                    val bytes: ByteArray = call.argument("bytes")!!
                    Thread {
                        kotlin.run {
                            connection!!.controlTransfer(
                                33, // (0x01 << 5)|0x01|0x00
                                9,
                                768 | bytes[0],
                                1,
                                bytes.size - 1,
                                bytes,
                                1000
                            )
                            result.success(0)
                        }
                    }.start()
                } else {
                    result.error("error", "error", "error")
                }
            }
            "getFeature" -> {
                if (connection != null) {
                    val bytes: ByteArray = call.argument("bytes")!!
                    Thread {
                        kotlin.run {
                            connection!!.controlTransfer(
                                161, //(0x01 << 5)|0x01|0x80
                                1,
                                768 | bytes[0],
                                1,
                                bytes.size - 1,
                                bytes,
                                1000
                            )
                            result.success(0)
                        }
                    }.start()
                } else {
                    result.error("error", "error", "error")
                }
            }            "close" -> {
                connection?.close()
                connection = null
                device = null
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