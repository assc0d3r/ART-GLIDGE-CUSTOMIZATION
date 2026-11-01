# Gboard Customizations

A Morphe patch bundle for Gboard (`com.google.android.inputmethod.latin`) containing:

- **Glide Trail Customization:** choose a solid trail color or rainbow effect, and adjust fade
  duration, width, and point retention.
- **Multilingual Suggestion:** optionally promote the first suggestion written in a different
  Unicode script beside Gboard's top candidate.
- **Keyboard Icon Packs:** apply imported WMKeyboard `.wmicons` or ZIP icons to supported Gboard
  keys and toolbar buttons. Import and enable packs from Theme Studio; selecting this patch also
  enables Theme Studio.
- **Gboard Theme Studio:** create, edit, duplicate, import, and export themes; edit key/board
  colors, key shapes, corner radius, and theme-family looks. The editor also offers image and
  texture backgrounds, wallpaper-following colors, animation, toolbar/popup styling, per-key
  overrides, sticker overlays, and an icon-pack preview. These extended styles are applied by
  runtime hooks in the patched keyboard; they are not all represented in Gboard's native ZIP
  theme format.
- **AMOLED Gboard Settings:** apply black backgrounds and legible text/control colors to
  AndroidX preference rows. Because the hook targets the shared preference binder, settings
  added by other patches are styled automatically when Gboard displays them.
- **XGesture Typing Controls:** optionally hold-drag the spacebar to move the cursor, use a
  two-finger spacebar drag to select text, swipe the delete key to remove a word or sentence,
  double-tap delete for undo, or hold delete for accelerated removal. Keyboard-edge swipes and
  bottom-corner editing tools remain available. Enable the separate spacebar drawing option to
  draw C/P/U/R for copy/paste/undo/redo; unrecognized strokes fall back to cursor movement. Define
  `@` snippets and macros from the corner menu. Gestures are disabled by default.

All patches are exposed as experimental, version-unpinned Gboard targets. They still rely on
Gboard's internal classes and settings resources; versions that change those structures may not
be patchable. Version-unpinned does not guarantee compatibility with every Gboard release.
AMOLED styling covers AndroidX preference screens; custom settings activities, dialogs, and
non-preference views are outside this shared row-binding hook, and future Gboard versions may
change the AndroidX class or binding method.

The multilingual option is a best-effort heuristic, not language detection: it preserves Gboard's
first candidate and promotes the first candidate with a different writing script. It affects both
glide and ordinary suggestion rows, cannot distinguish languages that use the same script (such as
English and Hinglish), and does not rotate between language models.

The icon-pack importer accepts `pack.json` version 1 with PNG, WebP, JPEG, or GIF icons. It validates
the archive and image dimensions before storing icons in Gboard's private app storage. SVG icons and
slots that cannot be mapped to Gboard metadata are not applied. Packs are imported and enabled from
Theme Studio, avoiding a duplicate icon-pack category in Glide typing settings. The icon patch is
experimental: Gboard changes its internal key rendering between releases, so an unpinned target does
not guarantee every version will patch or expose every icon.

Theme Studio imports and exports WMKeyboard `.wmtheme.json` files while preserving unrecognized
fields, and supports up to 13 looks and undo/redo. Applying a theme also creates a Gboard-native
CSS theme for the base colors and key shape; the runtime hook applies the additional editor styles
to Gboard views. Imported icon packs are managed in Theme Studio and are not embedded in theme
exports. Modifier-key and spacebar background/text colors can be edited separately, and the spacebar
is available in per-key styling. The runtime depends on Gboard's internal `SoftKeyView` render method
and view-tree naming, so extended styling is experimental, may not cover every surface, and is not
guaranteed to work with every Gboard version. Native archive validation depends on a validator
exposed by the installed Gboard build; unsupported builds receive an explicit activation error. The
patch and generated APK were smoke-tested against Gboard 18.5.6, but visual behavior still requires
device verification. The default/system selection restores captured view styling and removes the
custom native theme setting.

XGesture uses Gboard's keyboard-view and rendered-key hooks plus Android's active input connection.
It is version-sensitive and experimental. Snippets expand after their final trigger character;
macros support `paste`, `undo`, `redo`, `deleteWord`, `left`, `right`, `selectAll`, `copy`, and
`insertText:text` actions. Spacebar gesture drawing is separately configurable and recognizes
single-stroke C/P/U/R shapes after a hold; other strokes fall back to cursor movement. Recognition
depends on the path fitting within the spacebar area and needs device verification. Entries are
stored in Gboard preferences and are not encrypted; do not store passwords or active one-time
codes. It does not install system-edge, back-tap, shake, or floating-overlay handlers. Enable it
only if the hooks are available, then verify behavior on the target Gboard build and in the intended
input fields.

## Use in Morphe

1. Add this repository as a Morphe source:
   `https://github.com/assc0d3r/ART-GLIDGE-CUSTOMIZATION`
2. Select Gboard in the Patcher and enable the desired customization patch.
3. Apply the patch and install the generated APK.
4. Open **Gboard Theme Studio** from Gboard Settings. Import a `.wmicons`/ZIP pack there, enable
   supported icon replacement, and customize modifier/spacebar colors in the theme editor.
5. To try XGesture, enable **Enable keyboard-local gestures** in Gboard's gesture settings, and
   enable **Enable spacebar gesture drawing** for C/P/U/R. Hold a bottom corner to manage
   snippets/macros; type a saved `@trigger` to expand it.

## Build

Run `build.sh` with JDK 21 and Bash. The script downloads the Kotlin compiler, Morphe CLI, and Android build
dependencies when they are not already cached in `libs/`.

The Morphe patch bundle is generated at `dist/patches-1.0.21.mpp`. Add the repository to Morphe
to consume the release bundle; use the `.mpp` file directly when sideloading a local build.
