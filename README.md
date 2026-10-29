# Gboard Customizations

A Morphe patch bundle for Gboard (`com.google.android.inputmethod.latin`) containing:

- **Glide Trail Customization:** choose a solid trail color or rainbow effect, and adjust fade
  duration, width, and point retention.
- **Multilingual Suggestion:** optionally promote the first suggestion written in a different
  Unicode script beside Gboard's top candidate.
- **Keyboard Icon Packs:** import raster icons from a WMKeyboard `.wmicons` or ZIP pack and use
  matching icons for supported Gboard keys and toolbar buttons. The enabled state persists;
  slots without a known Gboard match are left unchanged.
- **Gboard Theme Studio:** create, edit, duplicate, import, and export themes; edit key/board
  colors, key shapes, corner radius, and theme-family looks.

All patches are exposed as experimental, version-unpinned Gboard targets. They still rely on
Gboard's internal classes and settings resources; versions that change those structures may not
be patchable. Version-unpinned does not guarantee compatibility with every Gboard release.

The multilingual option is a best-effort heuristic, not language detection: it preserves Gboard's
first candidate and promotes the first candidate with a different writing script. It affects both
glide and ordinary suggestion rows, cannot distinguish languages that use the same script (such as
English and Hinglish), and does not rotate between language models.

The icon-pack importer accepts `pack.json` version 1 with PNG, WebP, JPEG, or GIF icons. It validates
the archive and image dimensions before storing icons in Gboard's private app storage. SVG icons and
slots that cannot be mapped to Gboard metadata are not applied. The icon patch is also experimental:
Gboard changes its internal key rendering and settings structures between releases, so an unpinned
target does not guarantee every version will patch or expose every icon.

Theme Studio is an incremental first layer, not full WMKeyboard editor parity. It supports color
editing, a selection of key shapes, corner radius, board solid/linear-gradient fills, up to 13 looks,
undo/redo, and WMKeyboard `.wmtheme.json` import/export while preserving unrecognized JSON fields.
Imported colors may be hex strings or numbers. The current renderer does not yet implement wallpaper
color matching, background images, textures, animation, toolbar/popup/panel styling, per-key styles,
or stickers. The default/system selection leaves Gboard's stock rendering untouched.

## Use in Morphe

1. Add this repository as a Morphe source:
   `https://github.com/assc0d3r/ART-GLIDGE-CUSTOMIZATION`
2. Select Gboard in the Patcher and enable the desired customization patch.
3. Apply the patch and install the generated APK.
4. Configure the relevant option in Gboard Settings. For icon packs, open **Keyboard Icon Packs**,
   import the `.wmicons`/ZIP file, and enable **Use imported keyboard icons**.

## Build

Run `build.sh` with JDK 21 and Bash. The script downloads the Kotlin compiler, Morphe CLI, and Android build
dependencies when they are not already cached in `libs/`.

The Morphe patch bundle is generated at `dist/patches-1.0.7.mpp`. Add the repository to Morphe
to consume the release bundle; use the `.mpp` file directly when sideloading a local build.
