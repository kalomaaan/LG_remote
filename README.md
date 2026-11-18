# AC Remote — ad-free IR remote for LG aircons

A small Android app that turns your phone's **IR blaster** into a remote for LG air conditioners.
Built for the LG **LA080WC** Dual Inverter window aircon, and should work with most LG units
that use the standard 28-bit LG IR protocol.

No ads, no account, no internet permission.

## Download

Get the latest APK from **[Releases](../../releases/latest)**, open it on your phone and allow
"Install unknown apps" when Android asks.

## Features

- Power, Mode (Cool / Dry / Fan / Auto), Temp 16–30 °C, Fan speed (Low / Med / High / Auto)
- Jet Mode, Energy Saving, Light Off, Comfort Air (swing)
- LCD-style display that remembers the last state you sent
- 3 presets — tap to apply, long-press to save your current settings
- Off timer (30 m / 1 h / 2 h / 4 h) — the phone sends OFF even if the app is closed
- Settings: switch signal type A/B if your AC doesn't respond, and re-sync ON/OFF state

## Requirements

- Android 8.0+
- A phone with an IR blaster (Xiaomi, Redmi, POCO, Huawei, many Infinix & Tecno).
  Most Samsung and Pixel phones don't have one.

## Notes

IR is one-way: the app can't read the AC's state. If you use the physical remote too,
use **Settings → Out of sync?** to tell the app whether the AC is on or off.

Protocol details come from the open-source
[IRremoteESP8266](https://github.com/crankyoldgit/IRremoteESP8266) and
[ESPHome](https://esphome.io) projects. Not affiliated with or endorsed by LG Electronics.

## Building

Every push builds an APK with GitHub Actions. Pushing a tag like `v1.0.1` publishes a Release
with the APK attached.

```
gradle :app:assembleRelease
```

## License

MIT
