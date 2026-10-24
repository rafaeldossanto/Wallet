package com.wallet.wallet

import android.content.ActivityNotFoundException
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Android's own "Save as" for a file the app built in memory (lib/core/android/document_saver.dart),
 * through the Storage Access Framework: the user picks the folder (Downloads, Drive...) and the
 * bytes go straight there. No copy is left in the app's cache or in a temporary folder, and the file
 * is not offered to other apps the way the share sheet would.
 *
 * On the `wallet/document_saver` channel:
 * - `save(name, bytes)` opens the system's "Save as" with [name] suggested as an `application/json`
 *   file and answers the name the user ended up with, or null when they cancel (nothing is written).
 *   The errors are "busy" (another save is open), "invalid" (no name or bytes) and "failed".
 *
 * Created with the activity, before it starts, as the picker's result needs.
 */
class DocumentSaver(private val activity: FragmentActivity) {
    private val io = Executors.newSingleThreadExecutor()
    private var channel: MethodChannel? = null
    private var pending: Pending? = null

    private class Pending(val name: String, val bytes: ByteArray, val result: MethodChannel.Result)

    private val picker = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> onPicked(uri) }

    fun attach(messenger: BinaryMessenger) {
        channel = MethodChannel(messenger, CHANNEL).apply { setMethodCallHandler(::onCall) }
    }

    fun detach() {
        channel?.setMethodCallHandler(null)
        channel = null
        io.shutdown()
    }

    private fun onCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "save" -> save(call.argument<String>("name"), call.argument<ByteArray>("bytes"), result)
            else -> result.notImplemented()
        }
    }

    private fun save(name: String?, bytes: ByteArray?, result: MethodChannel.Result) {
        if (name.isNullOrBlank() || bytes == null) {
            result.error("invalid", "A name and the bytes are required", null)
            return
        }
        if (pending != null) {
            result.error("busy", "Another file is being saved", null)
            return
        }
        pending = Pending(name, bytes, result)
        try {
            picker.launch(name)
        } catch (error: ActivityNotFoundException) {
            pending = null
            result.error("failed", "No app can save a file", null)
        }
    }

    private fun onPicked(uri: Uri?) {
        val job = pending ?: return
        pending = null
        if (uri == null) {
            job.result.success(null)
            return
        }
        io.execute {
            try {
                // "wt": a file the user chose to replace is cut first, not overwritten in part.
                val stream = activity.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("No stream")
                stream.use { it.write(job.bytes) }
                val saved = displayName(uri) ?: job.name
                activity.runOnUiThread { job.result.success(saved) }
            } catch (error: Exception) {
                // The class only: a message can carry the path the user picked.
                activity.runOnUiThread { job.result.error("failed", error.javaClass.simpleName, null) }
            }
        }
    }

    private fun displayName(uri: Uri): String? =
        activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private companion object {
        const val CHANNEL = "wallet/document_saver"
    }
}
