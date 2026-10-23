package io.sundartv;

import android.util.Log;

/** The app's log (adb logcat -s SundarTV). Never logs keys. */
final class AppLog {
    private AppLog() {}

    static void w(String message) {
        Log.i("SundarTV", message);
    }
}
