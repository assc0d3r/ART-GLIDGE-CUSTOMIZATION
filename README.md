

Pasted markdown.md
Document
redeign the github page make it more intuitive attractive and visually striking for people to get attention
GLYPHCHEMY — redesigned GitHub landing page
89 patches preserved GitHub-ready

I redesigned the README around a more distinctive dark violet + cyan visual identity, with a stronger first impression and a much clearer path for new visitors.

Banner preview
View the full-resolution banner

Download the redesigned files
Complete redesign package

Recommended
Includes the new README and matching SVG banner, with the correct folder structure.

Download ZIP

README.md

Download redesigned README

SVG banner

Download banner

What changed
Area

Redesign

First impression

Custom branded hero banner

Branding

Stronger GLYPHCHEMY identity

Navigation

Quick-start and section links

Feature discovery

Eight expandable categories

Feature catalogue

All 89 entries retained

Installation

More prominent, simplified instructions

Compatibility

Clear version support matrix

Safety

Invasive disabled-by-default patches highlighted

Developer information

Build, Tasker and manifest instructions organized at the bottom

Installation into your repository:

Replace the existing README.md.

Add glyphchemy-banner.svg under assets/.

Commit both files and open the repository page.

The package passed structural checks for all 89 entries, internal links, SVG validity and ZIP integrity.

Note: The files are prepared and ready to commit; I have not pushed changes to your GitHub repository.


banner-preview.png
Image

README.md
Document

glyphchemy-banner.svg
Image
Your file library is full. New files won't be saved to your library, but you can still use them in this chat. To store new files for later, free up space or upgrade your storage.




<p align="center"> <img src="assets/glyphchemy-banner.svg" alt="Glyphchemy — Alchemy for Every Key" width="100%"> </p><p align="center"> <strong>Alchemy for Every Key.</strong><br> A bespoke <strong>Morphe patch collection for Gboard</strong> — reshape how your keyboard looks, feels and works. </p><p align="center"> <a href="https://morphe.software/add-source?github=assc0d3r/GLYPHCHEMY"><img alt="Add Glyphchemy to Morphe" src="https://img.shields.io/badge/ADD%20TO-MORPHE-8B5CF6?style=for-the-badge&logo=android&logoColor=white"></a> <a href="https://github.com/assc0d3r/GLYPHCHEMY/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/assc0d3r/GLYPHCHEMY?display_name=tag&style=for-the-badge&color=14B8A6"></a> <a href="https://github.com/assc0d3r/GLYPHCHEMY"><img alt="89 patches" src="https://img.shields.io/badge/PATCHES-89-0F172A?style=for-the-badge"></a> <img alt="Target Gboard" src="https://img.shields.io/badge/TARGET-GBOARD-4285F4?style=for-the-badge"> <img alt="GPL v3 license" src="https://img.shields.io/badge/LICENSE-GPL--3.0-64748B?style=for-the-badge"> </p><p align="center"> <a href="#quick-start">Quick start</a> · <a href="#compatibility">Compatibility</a> · <a href="#patch-catalogue">Explore all patches</a> · <a href="#build-from-source">Build from source</a> </p>
Your keyboard. Your rules.
Glyphchemy is an experimental Gboard customization and enhancement suite built with bespoke Morphe patches. It targets Gboard 18.6.3 and packages 89 individually selectable patches: most add project-built runtime features, while others expose behaviour already present in Gboard behind a rollout gate or hidden setting.

Use a single patch, assemble a personal setup, or combine Glyphchemy with other Morphe sources. The catalogue below is also generated into patches-list.json, including each patch’s description, options, default state and compatible versions.

<div align="center"> <table> <tr> <td align="center"><strong>89</strong><br><sub>selectable patches</sub></td> <td align="center"><strong>8</strong><br><sub>feature groups</sub></td> <td align="center"><strong>Gboard 18.6.3</strong><br><sub>primary target</sub></td> <td align="center"><strong>Morphe</strong><br><sub>patch delivery</sub></td> </tr> </table> </div>
Find your next upgrade
Feature group	What it covers
Typing, gestures & editing	Faster input, better cursor control, smarter gestures and tactile feedback
Themes, keys & visual effects	Theme creation, glass effects, key styling, icons and visual customization
Voice typing	Parakeet, Rambler, personal dictionaries, voice editing and OCR
Text & suggestions	Writing tools, grammar, inline suggestions and an in-keyboard calculator
Clipboard & quick actions	Clipboard controls, toolbar customization, web tools and quick insert
Layouts	Split/accessibility layouts, iOS-inspired layouts and Zhuyin enhancements
Settings & automation	Extra settings, backup/restore, branding, developer tools and Tasker events
Privacy & clean-up	Tracker controls, network restrictions, permissions and package clean-up
Quick start
Get Glyphchemy into Morphe, choose your patches, and build a patched Gboard APK.

Open Morphe on your Android device.

Go to Settings → Sources → Add Source.

Add this repository:

https://github.com/assc0d3r/GLYPHCHEMY
Open the Patcher tab and select Gboard 18.6.3.1002251336-release-arm64-v8a.

Choose the patches you want, tap Patch, then install the generated APK.

<p align="center"> <a href="https://morphe.software/add-source?github=assc0d3r/GLYPHCHEMY"><img alt="Add Glyphchemy source to Morphe" src="https://img.shields.io/badge/＋%20ADD%20SOURCE%20IN%20MORPHE-8B5CF6?style=for-the-badge"></a> </p>
Important — read before enabling invasive patches. Entries marked disabled by default are opt-in because they can alter package identity, remove network access or Play Services, turn the keyboard fully black, remove permissions, or enable shell access. Read their descriptions before switching them on.

Package-name warning: leave Gboard’s official package name unchanged unless you specifically need Change package name. A re-signed APK is not signed with Google’s key and therefore cannot replace an installed stock Gboard in place. To install a differently named patched copy, the built-in Gboard must first be uninstalled.

Compatibility
Gboard build	Coverage
18.6.3.1002251336-release-arm64-v8a	Full bundle coverage — every patch in this collection
18.8.0.1022711984-beta-arm64-v8a	Frosted Glass, Smart bar case swipe and Punctuation spacing; experimental: the abotmaker Rambler Waveform Animation and Productivity Tools ports
18.5.4.993608717-release-arm64-v8a	The newer typing, gesture, theme and field patches
18.0.3.954559732-release-arm64-v8a, 18.0.3	The three original patches: Glide Trail Customization, Backspace Repeat Haptic Feedback and Enter Key Tasker Event
Morphe skips a patch when its target signature is not present in the installed Gboard version; an unmatched patch does not fail the entire run.

Patch catalogue
All 89 patches, grouped by what they change. Expand a category, then open any patch for its full behaviour and configuration notes. “Disabled by default” is preserved wherever the patch is intentionally opt-in.

<a id="typing-gestures-and-editing"></a>

<details> <summary><strong>⌨️ Typing, Gestures and Editing</strong> <sub>· 14 patches</sub></summary><details> <summary><code>Per-app profiles</code></summary>
Applies a profile to the keyboard per app: incognito (no learning) and no suggestions for the packages in the local rule
table, plus a toolbar order and a theme or keyboard height written through Gboard's own toolbar editor and
<code>additional_keyboard_theme</code> preference. A rule can be scoped to a whole app or to one kind of field (text,
email, URL, number, phone, date, time, date-and-time, password or PIN, or a terminal with no field type). Several rules
can be kept and cleared from Gboard's settings as a <code>package=tokens</code> list next to the single-rule entry, and a
session override for the app in focus lasts until another app is focused.

</details><details> <summary><code>Field adaptation</code> (disabled by default)</summary>
Adjusts suggestions and auto-correct to the field being edited: search, URL, email, password and code fields each get
their own suggestion and correction behaviour. Key size and glide sensitivity are not touched.

</details><details> <summary><code>Swipe Left to Delete</code></summary>
Sliding left anywhere on the keyboard acts as backspace and deletes characters while you drag; keep holding to delete
whole words.

</details><details> <summary><code>Smart bar case swipe</code></summary>
Swipe up on a suggested word to capitalize it, or swipe down to lowercase it before it is inserted. The stock candidate
tap still runs, so the trailing space and suggestion bookkeeping are Gboard's own.

</details><details> <summary><code>Spacebar double-tap tab/undo</code></summary>
Double tap the left half of the spacebar to insert a tab, the right half to undo the last edit. The second tap is
dropped before Gboard commits a second space, and the tab replaces that first space instead of following it.

</details><details> <summary><code>Punctuation spacing</code></summary>
Deletes the space Gboard leaves before punctuation that the language sets tight against the word, so a typed
<code>.</code> after <code>Hey </code> commits as <code>Hey.</code>. Period, comma and ellipsis always hug; in French,
<code>;:!?</code> keep exactly one space and only extra spaces are removed. Multi-character commits, selections and
other languages are left untouched.

</details><details> <summary><code>English QWERTY Flick Case</code></summary>
Flick up for uppercase and flick down for lowercase on the English QWERTY keyboard. Flicking down also overrides
auto-capitalization.

</details><details> <summary><code>Multilingual Suggestion</code></summary>
Promotes glide and typing suggestions written in a different script, so a second-language word is not hidden behind
Latin candidates.

</details><details> <summary><code>Long-Press Editing Shortcuts</code></summary>
Adds Select all, Undo, Copy, Cut, Paste and Redo long-press shortcuts to English QWERTY and Zhuyin, with an optional
globe-key drag gesture that follows the same physical key positions across supported alphabet layouts.

</details><details> <summary><code>Cursor trackpad mode</code></summary>
Long-press the space bar to use cursor trackpad and lock modes.

</details><details> <summary><code>Swipe Down to Dismiss Keyboard</code></summary>
Swipe down from the suggestions row or the toolbar to close the keyboard.

</details><details> <summary><code>Latin Globe Key Ignore Interval</code> (disabled by default)</summary>
Adds an independent English globe-key ignore interval override, so a language switch can be delayed after typing
without changing the stock interval.

</details><details> <summary><code>Keypress Vibration Intensity Control</code></summary>
Controls keypress vibration intensity instead of duration.

</details><details> <summary><code>Backspace Repeat Haptic Feedback</code></summary>
Triggers a continuous tactile pulse for every deletion step while backspace is held down, using Gboard's native haptic
player. The pulses stop when the cursor reaches the start of the field and nothing is deleted.

</details></details>
<a id="themes-keys-and-visual-effects"></a>

<details> <summary><strong>🎨 Themes, Keys and Visual Effects</strong> <sub>· 17 patches</sub></summary><details> <summary><code>Gboard Theme Builder</code></summary>
Creates native Gboard themes and customizes key visuals through runtime hooks, so a theme can be built and edited
inside the patched app.

</details><details> <summary><code>Custom Theme</code></summary>
Imports custom ZIP themes from Gboard's own theme screen and browses ready-made theme packs, downloaded on demand and
verified by checksum. Adds the INTERNET permission.

</details><details> <summary><code>Theme Follows Dark Mode</code></summary>
Adds a "Light & dark pair" menu to the top of Gboard's Themes screen, so the keyboard switches theme when the system
dark mode changes. It can switch the wallpaper between a light and a dark image too.

</details><details> <summary><code>Glide Trail Customization</code></summary>
Customizes the glide typing trail: fade speed (200 ms – 4000 ms), thickness (2 dp – 40 dp), tail length (5 – 100
points) and colour, with a dynamic Rainbow RGB mode and an interactive HSV colour wheel with presets and a
reset-to-theme option. The controls live in a "Glide Trail Customizations" group under <em>Settings → Glide typing</em>.

</details><details> <summary><code>Keyboard Icon Packs</code></summary>
Applies compatible raster icon packs to the keyboard, with per-key icon overrides.

</details><details> <summary><code>Key Shape Selection</code></summary>
Enables Gboard's <em>Key shape</em> option in theme details, so a theme's keys follow the theme's own shape instead of
being forced square.

</details><details> <summary><code>Rounded Keyboard Panel</code></summary>
Customizes which corners of the keyboard panel are rounded, with separate top and bottom radii, for whichever theme is
active.

</details><details> <summary><code>Frosted Glass</code></summary>
Adds live background blur and custom-theme transparency to the keyboard, with separate background and key opacity and an
option to extend into the navigation bar. Blur needs Android 12 or newer.

</details><details> <summary><code>Glass Panel</code></summary>
Turns the translucent keyboard panel into glass: the system's own glass material on vivo OriginOS, lit edges and a sheen
elsewhere. Keys are left alone. Works on top of Translucent Bottom Bar and Frosted Glass.

</details><details> <summary><code>Glass Press Effect</code></summary>
Adds vivo-style liquid glass touch feedback: a soft light blooms out from your finger across the keyboard background,
and the keys around your finger dip and spring back.

</details><details> <summary><code>Translucent Bottom Bar</code></summary>
Makes the bar under the keyboard — behind the globe and close buttons — match the keyboard's colour, opacity and blur,
for use with transparent themes such as Frosted Glass.

</details><details> <summary><code>Immersive Keyboard</code></summary>
Extends the keyboard under the camera cutout in landscape instead of leaving an empty strip beside it.

</details><details> <summary><code>G Logo on Spacebar</code></summary>
Shows a built-in icon or your own PNG, WebP or JPEG on the spacebar. Keep its original colours or choose a single one,
adjust its size, and hide the language label.

</details><details> <summary><code>Emoji Key On Right</code></summary>
Moves the dedicated emoji key from the left of the spacebar to just left of the enter / search key.

</details><details> <summary><code>Change emoji size</code></summary>
Enables Gboard's emoji size setting.

</details><details> <summary><code>AMOLED dark keyboard</code> (disabled by default)</summary>
Experimental. Turns Gboard's dark keyboard background pure black instead of Google's grey, including the dynamic theme
that follows the wallpaper. Light mode and the light theme stay unchanged.

</details><details> <summary><code>AMOLED Gboard Settings</code></summary>
Applies a pure-black theme to Gboard's settings rows and to the preference screens the patches add.

</details></details>
<a id="voice-typing"></a>

<details> <summary><strong>🎙️ Voice Typing</strong> <sub>· 12 patches</sub></summary><details> <summary><code>Parakeet voice typing</code></summary>
Adds <em>Settings → Voice typing → Parakeet voice typing</em>. After downloading NVIDIA Parakeet (Moondream's Parakeet
Ultra or the original TDT v3), the microphone key transcribes on the phone and recognises Dutch, English and 23 more
languages per sentence without switching. Also gives the patched Gboard the Parakeet launcher icon.

</details><details> <summary><code>Parakeet personal dictionary</code></summary>
Lets Parakeet voice typing spell the names and words in the Voice dictionary's voice correction list, including contact
names added there. Parakeet favours them while listening and corrects a sound-alike spelling only when a second listen
agrees. Switch it on or off under Parakeet voice typing; it needs a 2 MB spelling download. Requires Parakeet voice
typing and Rambler personal dictionary.

</details><details> <summary><code>Rambler voice typing</code></summary>
Adds Google's Rambler to <em>Settings → Voice typing</em>, as a choice next to Standard voice typing, which Parakeet
voice typing renames to Parakeet. Rambler sends your speech to Google's servers, which turn it into finished text and
carry out spoken edits. Standard (Parakeet) stays the default.

</details><details> <summary><code>Rambler personal dictionary</code></summary>
Adds Voice dictionary to <em>Settings → Voice typing</em> with Gboard's voice correction list: names and words you add,
import or export, which Rambler receives with every dictation so it spells them right. Requires Rambler voice typing.

</details><details> <summary><code>Rambler contact names</code></summary>
Adds your contacts' names to Rambler's voice correction list, so Rambler spells them right. Names you delete from the
list are not added again; new contacts are added the next time Rambler starts. Needs Gboard's contacts access, which
Gboard asks for when you switch on Contact names. Requires Rambler personal dictionary.

</details><details> <summary><code>Rambler custom instructions</code></summary>
Adds Custom instructions to <em>Settings → Voice typing → Rambler</em>. Your text is added to Rambler's own
instructions, not a replacement for them: Rambler receives it with every dictation and follows it for style only —
spelling, punctuation, vocabulary, tone. For example: "Write every number in digits." Requires Rambler voice typing.

</details><details> <summary><code>Rambler cursor-aware editing</code></summary>
Rambler also sees the text after the cursor, so spoken edits reach the whole text and dictating in the middle of a text
fits in. With text selected, only the selection is sent, so an edit such as "make this more formal" applies to it, and
the result is selected again for the next edit. Requires Rambler voice typing.

</details><details> <summary><code>Rambler app context</code></summary>
Rambler also receives the name of the app you dictate in and the text field's hint, so it can match the tone of a chat,
an e-mail or a search, and custom instructions can depend on the app. This tells Google which app you are typing in.
Requires Rambler voice typing.

</details><details> <summary><code>Rambler stop after silence</code></summary>
Rambler stops by itself after 3 seconds without speech and keeps the text, as when you tap stop. Change the time (3, 5, 8
or 10 seconds) or switch it off under <em>Settings → Voice typing → Rambler → Stop after silence</em>. Requires Rambler
voice typing.

</details><details> <summary><code>Advanced Voice Typing</code></summary>
Enables Advanced Voice Typing with automatic punctuation, and separately enables automatic punctuation for Traditional
Chinese voice typing, which does not support Advanced Voice Typing.

</details><details> <summary><code>Use Bluetooth Microphone</code></summary>
Enables <em>Voice typing → Use Bluetooth microphone</em> and its rollout gate.

</details><details> <summary><code>Enable OCR / Scan Text</code></summary>
Enables Gboard's Scan Text camera entry point and runs it with an OCR engine bundled in the patch, so it works without
Play Services. Latin text only.

</details></details>
<a id="text-suggestions-and-editing"></a>

<details> <summary><strong>✍️ Text, Suggestions and Editing</strong> <sub>· 7 patches</sub></summary><details> <summary><code>AI Writing Tools</code></summary>
Enables Gboard's Writing tools: proofread, rewrite and tone changes on selected text, in every language and keyboard.

</details><details> <summary><code>Grammar Checker</code></summary>
Enables <em>Text correction → Grammar check</em> and its rollout gate.

</details><details> <summary><code>Inline Suggestions</code></summary>
Enables <em>Text correction → Smart Compose</em> and its rollout gate.

</details><details> <summary><code>Enable Inline Autofill Suggestions</code></summary>
Enables inline autofill suggestions in supported contexts.

</details><details> <summary><code>Dictionaries &amp; Predictions</code></summary>
Chooses resource versions for dictionaries, predictions and input correction.

</details><details> <summary><code>Simple Calculator</code></summary>
Type an arithmetic expression and get a scrollable equation plus its answer in Gboard's suggestion row. Tap the result
to replace the expression, or long-press it to copy the answer.

</details><details> <summary><code>Close Proactive Suggestions</code></summary>
Shows a dismiss button in the proactive suggestions bar.

</details></details>
<a id="clipboard-toolbar-and-quick-actions"></a>

<details> <summary><strong>📋 Clipboard, Toolbar and Quick Actions</strong> <sub>· 12 patches</sub></summary><details> <summary><code>Web Clipboard</code></summary>
Hosts a phone-powered Web Clipboard portal that lets desktop browsers sync with Gboard over the same LAN, behind a
pairing-code gate, with an optional Quick Settings Tile.

</details><details> <summary><code>Clipboard Enhancements</code></summary>
Enhances clipboard retention time, item count limit, preview lines, countdown and creation-time labels, order index and
column count.

</details><details> <summary><code>Clipboard Custom Character Limit</code></summary>
Sets the maximum number of characters stored for each text clipboard item, with Gboard's stock 20,000-character limit as
the default.

</details><details> <summary><code>Quick Insert</code></summary>
Enables the Quick Insert panel and its toolbar access point.

</details><details> <summary><code>Top Toolbar Item Count</code></summary>
Customizes how many items the top toolbar shows.

</details><details> <summary><code>Toolbar Editing Buttons</code></summary>
Adds Select All, Copy, Cut and Paste to the menu; drag them onto the top toolbar and use them with different keyboard
languages in supported editors.

</details><details> <summary><code>Custom Symbols</code></summary>
Adds a dedicated custom symbols tab and replaces the long-press comma entry with a heart shortcut.

</details><details> <summary><code>Access Points menu style</code> (disabled by default)</summary>
Switches between the new and legacy Access Points menu styles.

</details><details> <summary><code>Incognito Mode Toggle</code></summary>
Adds an Incognito toggle to the Access Point toolbar and configures clipboard and voice typing availability while
Incognito mode is active.

</details><details> <summary><code>FTP Server</code></summary>
Adds a LAN FTP server with file browsing, transfers and download resume, plus an optional Quick Settings Tile.

</details><details> <summary><code>Floating Web Search</code></summary>
Opens a floating web page directly from Gboard to look something up without leaving the app you are typing in.

</details><details> <summary><code>Swipeable Custom Top Row</code></summary>
Swipes the keyboard top row horizontally to switch between the stock row and a customizable text / JavaScript row.

</details></details>
<a id="layouts"></a>

<details> <summary><strong>⌨️ Layouts</strong> <sub>· 8 patches</sub></summary><details> <summary><code>iOS QWERTY layout</code> (disabled by default)</summary>
Puts the globe key of the letter keyboards at the bottom left, where it is on the Japanese 12-key keyboards, and lays
out the English symbol keyboard like the iOS "123" and "#+=" pages. Gboard's number row setting keeps working.

</details><details> <summary><code>iOS flick layout</code> (disabled by default)</summary>
Changes the Japanese 12-key keyboard to the iOS layout with "flick only" enabled: 123, ABC and symbol keys and the globe
key on the left, backspace, space and a two-row enter key on the right.

</details><details> <summary><code>Enable split keyboard</code></summary>
Enables Gboard's split keyboard layout.

</details><details> <summary><code>Enable accessibility layout</code></summary>
Enables Gboard's accessibility layout.

</details><details> <summary><code>Zhuyin Slide Input</code></summary>
Enables slide-up and slide-down input on the Zhuyin keyboard, so English letters can be typed without switching to
another layout.

</details><details> <summary><code>Zhuyin Bottom Row Key Sizes</code></summary>
Adjusts the seven bottom-row slot sizes on the Zhuyin keyboard: <code>?123</code>, <code>，</code>, the globe key,
space, <code>ㄦ</code>, backspace and the IME action key.

</details><details> <summary><code>Zhuyin Quick Traditional/Simplified Toggle</code></summary>
Swipe up on the Zhuyin <code>ㄥ</code> key to quickly toggle between Traditional and Simplified Chinese.

</details><details> <summary><code>Emojis, stickers &amp; GIFs Tab Order</code></summary>
Customizes the bottom tab order in Gboard's Emojis, stickers & GIFs panel with drag-and-drop reordering.

</details></details>
<a id="settings-branding-and-automation"></a>

<details> <summary><strong>⚙️ Settings, Branding and Automation</strong> <sub>· 7 patches</sub></summary><details> <summary><code>Keyboard Settings</code></summary>
Adds a settings screen inside Gboard that gathers the patches' own preferences — theme, vibration, clipboard and more.

</details><details> <summary><code>Settings Homepage Override</code></summary>
Switches between the new and legacy Gboard settings homepage.

</details><details> <summary><code>Developer options</code></summary>
Enables Developer options and the Flag Editor, so flag values can be read and changed.

</details><details> <summary><code>Backup &amp; Restore</code></summary>
Exports or restores all Patches settings, and backs up, compares or restores the Gboard PB/XML flag store.

</details><details> <summary><code>Change package name</code> (disabled by default)</summary>
Installs Gboard under its own package name, alongside the stock Gboard most phones have built in. Off by default:
Gboard features tied to the stock package name keep working only when the package is left alone. Enabling it requires
uninstalling the built-in Gboard first, because a patched copy is not signed with Google's key.

</details><details> <summary><code>Custom branding</code> (disabled by default)</summary>
Changes Gboard's app name and launcher icon: the Parakeet icon (default), Gboard's original icon or one of five other
designs. The name also appears in Android's keyboard list.

</details><details> <summary><code>Enter Key Tasker Event</code></summary>
Broadcasts an Intent event to Tasker whenever the Enter key or the IME action key (Send, Search, Go, Done, Next) is
pressed on Gboard. See <a href="#tasker-integration">the Tasker recipe</a> for the event and its variables.

</details></details>
<a id="privacy-and-clean-up"></a>

<details> <summary><strong>🔒 Privacy and Clean-up</strong> <sub>· 12 patches</sub></summary><details> <summary><code>Block Trackers</code></summary>
Stops background metrics, event logging and crash reporting.

</details><details> <summary><code>Block App Hijacking</code></summary>
Protects against other apps hijacking Gboard and removes debug tools.

</details><details> <summary><code>Stop Remote Updates</code></summary>
Stops Google from changing Gboard's settings remotely.

</details><details> <summary><code>Stop Background Sync</code> (disabled by default)</summary>
Stops background downloads and sync to save battery.

</details><details> <summary><code>Disable Cloud Backup</code></summary>
Keeps keyboard data off Google Drive backups.

</details><details> <summary><code>Disable Play Services</code> (disabled by default)</summary>
Runs Gboard without Google Play services. Also turns off inline Translate.

</details><details> <summary><code>Offline Only</code> (disabled by default)</summary>
Fully disconnects Gboard from the network. Download the dictionaries you need before applying it.

</details><details> <summary><code>Remove Permissions</code> (disabled by default)</summary>
Removes the permissions you select in the patch options.

</details><details> <summary><code>Remove Extra Files</code></summary>
Deletes licences, tutorial animations and other files Gboard does not need at runtime.

</details><details> <summary><code>Remove Translate Limit</code></summary>
Removes the character limit on inline translation.

</details><details> <summary><code>Add Gboard Signature Bypass</code></summary>
Bypasses Gboard's own APK signature whitelist so a patched, re-signed Gboard can use features that verify the signer,
such as themes.

</details><details> <summary><code>Key Mapper Watchdog</code> (disabled by default)</summary>
Uses Shizuku to restart Key Mapper's Expert Mode system bridge when it stops. This lets Gboard run shell commands, so
only enable it if that is what you want.

</details></details>
Tasker integration
Enter Key Tasker Event
The Enter Key Tasker Event patch broadcasts dev.custom.gboard.ENTER_PRESSED whenever the Enter key or IME action key (Send, Search, Go, Done or Next) is pressed.

Open Tasker → Profiles.

Tap + → Event → System → Intent Received.

Set Action to:

dev.custom.gboard.ENTER_PRESSED
Leave Cat, Scheme, Mime and Path blank, save the profile, and link a task.

Tasker variable	Meaning	Example
%package	Package name of the active foreground app	com.whatsapp, com.google.android.apps.messaging
%action_type	Action triggered	ENTER, SEND, SEARCH, GO, DONE, NEXT
%key_code	Raw keycode integer	66 (Enter), 160 (Numpad Enter), -10018 (Action)
%text_before	Text before the cursor, up to 100 characters	"Order #12345"
%timestamp	Epoch time in milliseconds	1726831000000
Build from source
Clone the repository and run the build script:

git clone https://github.com/assc0d3r/GLYPHCHEMY.git
cd GLYPHCHEMY

# Compile the patches and package the release bundle (.mpp)
./build.sh
The script resolves the pinned Kotlin compiler and the Morphe/D8/Android jars into libs/, compiles Kotlin patches by source group, checks the Smart bar case swipe hook register, compiles the injected Java runtimes into colorwheel.dex, packages the bundle, and lists every patch through the Morphe CLI as a smoke test.

Build outputs
Output	Description
dist/patches-<version>.mpp	Morphe release bundle. The version comes from version= in gradle.properties.
dist/gboard-backspace-haptics.jar	The same bundle packaged as a plain JAR for local use.
Regenerate patches-list.json
patches-list.json is the inventory consumed by Morphe sources and the release workflow. Generate it from a built bundle — do not hand-edit it.

java -cp "libs/morphe-cli.jar;libs/gson.jar" tools/bindings/Inventory.java \
  dist/patches-$(sed -n 's/^version=//p' gradle.properties).mpp \
  $(sed -n 's/^version=//p' gradle.properties) \
  patches-list.json
Use : instead of ; as the classpath separator on macOS and Linux. The generator keeps existing entry order for patches that remain, appends new patches in name order to keep diffs reviewable, and writes the file in place. Pass a fourth argument to write to another path.

License
Released under the GNU General Public License v3.0. See LICENSE.

<p align="center"> <sub>GLYPHCHEMY · Alchemy for Every Key · Built for Gboard, powered by Morphe.</sub> </p>
