# SundarTV

**A beautiful, ad-free home screen for Android TV and Google TV.** Made by JC.

*Sundar* means beautiful. SundarTV replaces your TV's cluttered, ad-filled home screen with a calm one that
follows the sky outside.

## What you get

- **The sky outside, live.** The background follows your city's real weather: clear blue with the sun's glow,
  clouds, fog, a rainy window with drops on frosted glass, falling snow, and thunderstorms with lightning. Rain and
  snow move; day turns to night at your real sunset.
- **No ads.** No sponsored rows, banners or autoplaying promos.
- **Your apps up front.** The streaming apps you use, on a glass shelf you arrange on the TV.
- **A quote screensaver.** A big, funny quote on black, a new one every two minutes, with the time, date and
  weather underneath.
- **The clock and the weather** at a glance, with text colors that match the sky.
- **Safe.** No root, nothing uninstalled. One button brings back the TV's original home screen.

## Install (Android TV / Google TV)

You need an Android TV or Google TV (Android 8 or newer) and a computer on the same Wi-Fi.

1. **On the TV:** Settings → System (or Device Preferences) → About → press *Build* 7 times. Then in Developer
   options, turn on **USB debugging**. Note the TV's IP address (Settings → Network & Internet).
2. **On the computer:** a JDK 17+, the Android SDK command-line tools (build-tools 34+, platform android-34) and
   `adb`. On Windows, run the build from Git Bash.
3. **Build:** `./build.sh`. The first run creates `config.properties`: set your city (`latitude`, `longitude`,
   `city`), your country (`tmdb_region`) and, if you like, a free TMDB key for trending picks. Run it again.
4. **Install:**
   ```
   adb connect <TV-IP>:5555
   adb install -r SundarTV.apk
   ```
5. **Switch on the TV:** open SundarTV → Home Screen → SundarTV, and accept *Allow USB debugging* (tick *Always
   allow*).

To pick when the screensaver starts: Settings → Device Preferences → Screen saver → When to start.

Keep `sundartv.keystore`: updates must be signed with the same key.

## Going back

SundarTV → Home Screen → **Original** brings back the TV's home screen, screensaver and every app it turned off.
If the home screen ever crashes three times in five minutes, it switches back by itself. To remove it, switch to
Original first, then uninstall SundarTV (Settings → Apps).

## LG and Samsung

The same home screen also builds as an app for LG (webOS 3+) and Samsung (Tizen 3+) TVs: `python3 webos/build.py`
and `python3 tizen/build.py`. See `CLAUDE.md` for the steps.

## How it works

A plain Android app in Java: no Gradle, no libraries. `build.sh` runs aapt2 → javac → d8 → zipalign → apksigner.
It switches the home screen with the TV's own ADB from inside the TV (that's the *Allow USB debugging* prompt), the
same commands you'd type yourself. The weather sky is drawn in `WeatherSky.java`, the screensaver quotes are in
`Sayings.java`.

## License and credits

MIT. Copyright (c) 2026 JC. SundarTV started from Free My TV (MIT); see [NOTICE.md](NOTICE.md) for that credit and
the licenses of the fonts, icons and data it uses.
