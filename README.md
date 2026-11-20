# Gboard Enhancement Patches Suite

<p align="center">
  <a href="https://morphe.software/add-source?github=ausamnco/gboard-enc-patches"><img alt="Add to Morphe" src="https://img.shields.io/badge/Morphe-Add%20Source-00A8FF?style=for-the-badge"></a>
  <img alt="Gboard" src="https://img.shields.io/badge/Target-Gboard-4285F4?style=for-the-badge">
  <img alt="License" src="https://img.shields.io/badge/License-GPLv3-green?style=for-the-badge">
</p>

A standalone, Morphe/ReVanced-compatible bytecode and resource patch suite for **Gboard** (`com.google.android.inputmethod.latin`).

The bundle ships 88 independent patches: every one can be selected on its own or combined in the **Morphe** app alongside other third-party patch sources. They are grouped by what they change:

- **Typing, gestures and input**: per-app profiles (incognito, no suggestions, toolbar order and theme or keyboard height per app), swipe left anywhere to delete, swipe a suggested word to change its case, spacebar double-tap undo/redo, English QWERTY up/down flick case, field adaptation for search, URL, email, password and code fields, multilingual suggestions, swipe down to dismiss, cursor trackpad, keypress vibration intensity, long-press editing shortcuts, backspace repeat haptics and the Tasker Intent event.
- **Themes, keys and visual effects**: Gboard Theme Builder, glide trail customization (speed, thickness, tail length, Rainbow RGB, HSV colour wheel), Custom Theme ZIP import and theme packs, icon packs, Frosted Glass, Glass Panel and Glass Press Effect, translucent bottom bar, rounded keyboard panel, key shape selection, immersive keyboard, theme that follows dark mode, AMOLED settings and dark keyboard, G logo on the spacebar, emoji key position and emoji size.
- **Voice typing**: Parakeet on-device voice typing and its personal dictionary, the Rambler cloud voice typing family (personal dictionary, contact names, custom instructions, cursor-aware editing, app context, stop after silence), Advanced Voice Typing, Bluetooth microphone and OCR / Scan Text.
- **Text, suggestions and editing**: AI Writing Tools (proofread, rewrite, tone), grammar check, inline suggestions, inline autofill, simple calculator, proactive suggestion dismiss button and dictionary / prediction resource versions.
- **Clipboard, toolbar and quick actions**: Web Clipboard with browser sync, clipboard retention and character-limit controls, Quick Insert, toolbar item count and editing buttons, custom symbols, Access Points menu style, Incognito toggle, FTP server and floating web search.
- **Layouts**: iOS QWERTY and iOS flick layouts, split and accessibility layouts, Zhuyin slide input, bottom-row key sizes and Traditional/Simplified toggle, emoji/sticker/GIF tab order.
- **Settings and branding**: a Keyboard Settings screen inside Gboard, settings homepage override, Developer options and Flag Editor, Backup & Restore, Change package name and Custom branding.
- **Privacy and clean-up**: Block Trackers, Block App Hijacking, Stop Remote Updates, Stop Background Sync, Disable Cloud Backup, Remove Permissions, Offline Only, Remove Extra Files, Disable Play Services, Remove Translate Limit, Key Mapper Watchdog and the Gboard signature bypass.

The full inventory, including descriptions, default on/off state and supported versions, is `patches-list.json` and the patch list inside Morphe. The three sections further down still document the original patches (Backspace Repeat Haptic Feedback, Enter Key Tasker Event and Glide Trail Customization) in depth.

---

## 📱 Quick Setup: Use in Morphe App

1. **Add Repository Source**:
   - Open **Morphe** on your phone.
   - Go to **Settings** ⚙️ -> **Sources**.
   - Tap **Add Source** and enter:
     ```
     https://github.com/ausamnco/gboard-enc-patches
     ```
   - Alternatively, tap the **Add to Morphe** button above from your Android browser.

2. **Patch Gboard**:
   - In Morphe, navigate to the **Patcher** tab.
   - Select **Gboard** (`18.6.3.1002251336-release-arm64-v8a` applies every patch; `18.5.4.993608717-release-arm64-v8a` and `18.0.3.954559732-release-arm64-v8a` cover subsets).
   - In the patch selection list you will find all 88 patches, grouped by category (typing and gestures, themes and visual effects, voice typing, text tools, clipboard and toolbar, layouts, settings and branding, privacy and clean-up).
   - Select the patches you desire and tap **Patch**!
   - Install the generated APK.

---

## ⚡ Patch 1: Backspace Repeat Haptic Feedback

- **How it works:** Hooks into Gboard's repeat key dispatch pipeline. While backspace is held down, every deletion step fires a tactile pulse via Gboard's native haptic player.
- **Smart suppression:** If the cursor is at the beginning of a line/field and no text is deleted, vibration is automatically suppressed.
- **Gboard Settings:** Adds a toggle under *Settings -> Preferences -> Key tap* (*"Backspace repeat haptic feedback"*).

---

## ⚡ Patch 2: Enter Key Tasker Event

- **How it works:** Hooks into Gboard's central input dispatch pipeline (`GoogleInputMethodService.dD`). Whenever the bottom-right key is pressed—whether for a standard newline (keycode 66/160) or an IME action (Send, Search, Go, Done, Next)—an Intent broadcast is immediately dispatched with zero latency.
- **Gboard Settings:** Adds a toggle under *Settings -> Preferences -> Key tap* (*"Send Enter key to Tasker"*).

### Tasker Setup Guide

1. Open **Tasker** -> go to the **Profiles** tab.
2. Tap **+** -> select **Event** -> **System** -> **Intent Received**.
3. In the **Action** field, enter:
   ```
   dev.custom.gboard.ENTER_PRESSED
   ```
4. Leave Cat, Scheme, Mime, and Path blank. Tap the back button to save the event.
5. Link a new Task. Inside the Task, you can use the following local variables:

| Tasker Variable | Description | Example Values |
| :--- | :--- | :--- |
| `%package` | Package name of the active foreground app | `com.whatsapp`, `com.google.android.apps.messaging` |
| `%action_type` | Action triggered | `ENTER`, `SEND`, `SEARCH`, `GO`, `DONE`, `NEXT` |
| `%key_code` | Raw keycode integer | `66` (Enter), `160` (Numpad Enter), `-10018` (Action) |
| `%text_before` | Text before the cursor (up to 100 chars) | `"Order #12345"` |
| `%timestamp` | Epoch time in milliseconds | `1726831000000` |

#### Example Tasker Actions:
- Flash a notification: `Enter pressed in %package: %action_type`
- Conditional action: Only run when pressed in WhatsApp:
  ```
  If %package ~ com.whatsapp
  ```

---

## ⚡ Patch 3: Glide Trail Customization

Customizes Gboard's glide typing gesture trail with full visual control inspired by Samsung Keyboard's Keys Cafe:

- **How it works:** Hooks Gboard's `GestureOverlayView` and trail point fade processor (`mvs.g`). Replaces the hardcoded 1000ms fade timer with a dynamically configurable decay clock and adjusts stroke width, point retention buffer, alpha decay rate, and paint color filter in real time.
- **Interactive HSV Color Wheel:** Tap the color wheel setting to open a full color picker dialog featuring a radial HSV wheel, brightness slider, live hex code preview, 8 quick presets, and a "Reset to Theme" option.
- **Dynamic Rainbow RGB Mode:** Continuously cycles through the HSV color spectrum based on system uptime, giving the trail a vivid moving RGB wave effect while gliding.
- **Continuous Control Sliders:**
  - **Fade Duration (Speed):** Slider from 200ms (ultra-fast, snappy) to 4000ms (slow lingering ribbon) with live numeric value display.
  - **Width & Thickness:** Slider from 2dp (thin hairline) to 40dp (thick prominent glow) with live numeric value display.
  - **Length & Retention:** Slider from 5 points (tight compact tail) to 100 points (extended full trail ribbon) with live numeric value display.
- **Grouped Settings:** All customization controls are cleanly grouped under a dedicated *"Glide Trail Customizations"* category directly beneath the stock "Show gesture trail" toggle in *Settings -> Glide typing*, and automatically enable/disable together.

---

## 🛠️ Building Locally

```bash
# Clone the repository
git clone https://github.com/ausamnco/gboard-enc-patches.git
cd gboard-enc-patches

# Compile patches and generate release bundle (.mpp)
./build.sh
```

Compiled outputs will be located in `dist/`:
- `dist/patches-1.0.56.mpp`: Release bundle for Morphe Manager. The version comes from `version=` in `gradle.properties`, so the file name follows it.
- `dist/gboard-backspace-haptics.jar`: The same bundle as a plain JAR, for local use.

---

## 🧾 Regenerating `patches-list.json`

`patches-list.json` is the inventory asset the Morphe sources and the release workflow read, so it has to describe every patch the bundle actually offers. It is generated from a built bundle, never hand-edited. After `./build.sh`, regenerate it in place with:

```bash
java -cp "libs/morphe-cli.jar;libs/gson.jar" tools/bindings/Inventory.java dist/patches-$(sed -n 's/^version=//p' gradle.properties).mpp $(sed -n 's/^version=//p' gradle.properties) patches-list.json
```

Use `:` instead of `;` as the classpath separator on macOS/Linux. The command runs the generator straight from source (JDK 11+) against `dist/patches-<version>.mpp`; it keeps the existing entry order for patches that still exist, appends new ones in name order so the diff stays reviewable, and writes `patches-list.json` back in place. Pass a fourth argument to write somewhere else instead.


