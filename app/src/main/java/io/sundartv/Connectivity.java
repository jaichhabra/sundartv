package io.sundartv;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

/**
 * What the Wi-Fi and Bluetooth pills at the top right of the home screen say. Joining a network or pairing a device
 * is the TV's own Settings' job (apps can't), so the pills open those pages directly (HomeApps.open).
 */
final class Connectivity {
    private Connectivity() {}

    /** The Wi-Fi network's name, "Ethernet", or "Not connected". */
    static String network(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo n = cm == null ? null : cm.getActiveNetworkInfo();
            if (n != null && n.isConnected()) {
                if (n.getType() == ConnectivityManager.TYPE_ETHERNET) return Ui.tr("Ethernet");
                if (n.getType() != ConnectivityManager.TYPE_WIFI) return Ui.tr("Connected");
                WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                String ssid = wm == null ? null : wm.getConnectionInfo().getSSID();
                // Android quotes the name; "<unknown ssid>" when it won't say.
                if (ssid != null && ssid.length() > 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                    return ssid.substring(1, ssid.length() - 1);
                }
                return Ui.tr("Wi-Fi");
            }
        } catch (RuntimeException ignored) {
        }
        return Ui.tr("Not connected");
    }

    /** "Off", "On", or "Connected" (a speaker, headphones, a keyboard or a remote); null on a TV without Bluetooth. */
    static String bluetooth() {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null) return null;
            if (!a.isEnabled()) return Ui.tr("Off");
            // 4: BluetoothProfile.INPUT_DEVICE (hidden in the SDK), remotes and keyboards.
            for (int profile : new int[] {BluetoothProfile.A2DP, BluetoothProfile.HEADSET, 4}) {
                if (a.getProfileConnectionState(profile) == BluetoothProfile.STATE_CONNECTED) return Ui.tr("Connected");
            }
            return Ui.tr("On");
        } catch (RuntimeException e) {
            return Ui.tr("On");
        }
    }
}
