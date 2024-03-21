package com.rustui.hid_android

import com.google.gson.annotations.SerializedName

class HidDevice(
    @SerializedName("vendorId")
    val vendorId: Int,
    @SerializedName("productId")
    val productId: Int,
    @SerializedName("serialNumber")
    val serialNumber: String,
    @SerializedName("productName")
    val productName: String,
    @SerializedName("deviceName")
    val deviceName: String
) {
}