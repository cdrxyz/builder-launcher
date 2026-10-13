package xyz.cdr.builderlauncher.data

import kotlinx.serialization.Serializable

@Serializable
data class PinChange(
    val packageName: String,
    val pinned: Boolean,
    val at: Long,
)
