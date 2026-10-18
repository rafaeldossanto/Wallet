package com.wallet.wallet

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.util.concurrent.Executors

/**
 * Hands an update downloaded by the app (lib/core/android/package_installer.dart) to Android's
 * package installer, through a session: the current API, where ACTION_INSTALL_PACKAGE is
 * deprecated. The APK is copied from the app's private storage into the session, so no file is
 * shared with other apps.
 *
 * On the `wallet/package_installer` channel:
 * - `install(path)` answers once the install is over: "cancelled" when the user says no to
 *   Android's confirmation, or the error "not-allowed" (Wallet may not install apps yet),
 *   "conflict" (the APK is signed with another key) or "failed". When the user says yes, Android
 *   replaces the app and this process ends before any answer.
 * - `allowInstalls` opens the setting where the user lets Wallet install apps and answers, once
 *   they are back, whether they did.
 *
 * Created with the activity, before it starts, as the settings screen's result needs.
 */
class UpdateInstaller(private val activity: FragmentActivity) {
    private val io = Executors.newSingleThreadExecutor()
    private var channel: MethodChannel? = null
    private var installing: MethodChannel.Result? = null
    private var allowing: MethodChannel.Result? = null
    private var listening = false

    private val settings = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        allowing?.success(canInstall())
        allowing = null
    }

    private val status = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onStatus(intent)
    }

    fun attach(messenger: BinaryMessenger) {
        channel = MethodChannel(messenger, CHANNEL).apply { setMethodCallHandler(::onCall) }
    }

    fun detach() {
        channel?.setMethodCallHandler(null)
        channel = null
        if (listening) {
            activity.unregisterReceiver(status)
            listening = false
        }
        io.shutdown()
    }

    private fun onCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "install" -> install(call.argument<String>("path"), result)
            "allowInstalls" -> allowInstalls(result)
            else -> result.notImplemented()
        }
    }

    /** "Instalar apps desconhecidos": Android 8 asks it per app; before, one switch covered all. */
    private fun canInstall() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()

    private fun install(path: String?, result: MethodChannel.Result) {
        val apk = path?.let(::File)
        if (apk == null || !apk.isFile) {
            result.error("failed", "No APK at $path", null)
            return
        }
        if (!canInstall()) {
            result.error("not-allowed", "Wallet may not install apps yet", null)
            return
        }
        // A new try replaces one whose confirmation was left open.
        installing?.success("cancelled")
        installing = result
        listen()
        io.execute {
            try {
                commit(apk)
            } catch (error: Exception) {
                activity.runOnUiThread { finish { it.error("failed", error.message, null) } }
            }
        }
    }

    private fun commit(apk: File) {
        val installer = activity.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(activity.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12 would let an app update itself without asking; Wallet always asks.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("wallet.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                session.commit(statusSender(sessionId))
            }
        } catch (error: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw error
        }
    }

    /** Where Android tells how the session went: [status], inside this app only. */
    private fun statusSender(sessionId: Int): IntentSender {
        val intent = Intent(STATUS_ACTION).setPackage(activity.packageName)
        // Mutable: the installer adds the status to it.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(activity, sessionId, intent, flags).intentSender
    }

    private fun listen() {
        if (!listening) {
            ContextCompat.registerReceiver(
                activity, status, IntentFilter(STATUS_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            listening = true
        }
    }

    private fun onStatus(intent: Intent) {
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            // The confirmation: "Atualizar este app?".
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirmation == null) {
                    finish { it.error("failed", "The installer sent no confirmation", null) }
                } else {
                    activity.startActivity(confirmation)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> finish { it.success("installed") }
            PackageInstaller.STATUS_FAILURE_ABORTED -> finish { it.success("cancelled") }
            PackageInstaller.STATUS_FAILURE_CONFLICT -> finish { it.error("conflict", message, null) }
            else -> finish { it.error("failed", message, null) }
        }
    }

    private fun finish(answer: (MethodChannel.Result) -> Unit) {
        installing?.let(answer)
        installing = null
    }

    private fun allowInstalls(result: MethodChannel.Result) {
        if (canInstall()) {
            result.success(true)
            return
        }
        allowing?.success(false)
        allowing = result
        try {
            settings.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")),
            )
        } catch (error: ActivityNotFoundException) {
            // A system without that screen: its general settings, where the user finds it.
            settings.launch(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private companion object {
        const val CHANNEL = "wallet/package_installer"
        const val STATUS_ACTION = "com.wallet.wallet.UPDATE_STATUS"
    }
}
