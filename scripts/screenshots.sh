#!/bin/bash
# Emülatörde uygulamayı açıp ekran görüntüleri alır (GitHub Actions "Ekran görüntüleri" iş akışı kullanır).
set -e
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.havaduru.app android.permission.POST_NOTIFICATIONS || true
adb shell am start -n com.havaduru.app/.MainActivity
sleep 30
mkdir -p screenshots
adb exec-out screencap -p > screenshots/01-ana-ekran.png
for i in 2 3 4 5 6 7; do
  adb shell input swipe 540 1900 540 700 500
  sleep 5
  adb exec-out screencap -p > "screenshots/0$i.png"
done
adb shell input keyevent KEYCODE_HOME
sleep 2
adb exec-out screencap -p > screenshots/08-ana-sayfa.png
