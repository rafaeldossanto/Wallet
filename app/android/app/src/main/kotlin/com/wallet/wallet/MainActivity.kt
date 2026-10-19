package com.wallet.wallet

import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine

// local_auth shows the system biometric prompt, which needs a FragmentActivity.
class MainActivity : FlutterFragmentActivity() {
    // Updates downloaded from the Releases, handed to Android's installer.
    private val updates = UpdateInstaller(this)

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        updates.attach(flutterEngine.dartExecutor.binaryMessenger)
    }

    override fun onDestroy() {
        updates.detach()
        super.onDestroy()
    }
}
