# Gboard Customizations

A Morphe patch bundle for Gboard (`com.google.android.inputmethod.latin`) containing:

- **Glide Trail Customization:** choose a solid trail color or rainbow effect, and adjust fade
  duration, width, and point retention.
- **Multilingual Suggestion:** optionally promote the first suggestion written in a different
  Unicode script beside Gboard's top candidate.
- **Keyboard Icon Packs:** import raster icons from a WMKeyboard `.wmicons` or ZIP pack and use
  matching icons for supported Gboard keys and toolbar buttons. The runtime honors Gboard's
  persistent switch state and uses key metadata and accessibility labels to find supported slots.
- **Gboard Theme Studio:** create, edit, duplicate, import, and export themes; edit key/board
  colors, key shapes, corner radius, and theme-family looks. The editor also offers image and
  texture backgrounds, wallpaper-following colors, animation, toolbar/popup styling, per-key
  overrides, sticker overlays, and an icon-pack preview. These extended styles are applied by
  runtime hooks in the patched keyboard; they are not all represented in Gboard's native ZIP
  theme format.

All patches are exposed as experimental, version-unpinned Gboard targets. They still rely on
Gboard's internal classes and settings resources; versions that change those structures may not
be patchable. Version-unpinned does not guarantee compatibility with every Gboard release.

The multilingual option is a best-effort heuristic, not language detection: it preserves Gboard's
first candidate and promotes the first candidate with a different writing script. It affects both
glide and ordinary suggestion rows, cannot distinguish languages that use the same script (such as
English and Hinglish), and does not rotate between language models.

The icon-pack importer accepts `pack.json` version 1 with PNG, WebP, JPEG, or GIF icons. It validates
the archive and image dimensions before storing icons in Gboard's private app storage. SVG icons and
slots that cannot be mapped to Gboard metadata are not applied. The enable switch is mirrored from
Gboard's preference listener so its runtime state stays aligned with Gboard Settings. The icon patch
is also experimental: Gboard changes its internal key rendering and settings structures between
releases, so an unpinned target does not guarantee every version will patch or expose every icon.

Theme Studio imports and exports WMKeyboard `.wmtheme.json` files while preserving unrecognized
fields, and supports up to 13 looks and undo/redo. Applying a theme also creates a Gboard-native
CSS theme for the base colors and key shape; the runtime hook applies the additional editor styles
to Gboard views. Imported icon packs remain in the separate Keyboard Icon Packs setting and are
not embedded in theme exports. The runtime depends on Gboard's internal `SoftKeyView` render method
and view-tree naming, so extended styling is experimental, may not cover every surface, and is not
guaranteed to work with every Gboard version. Native archive validation currently uses the Gboard
18.5.4 validator; other Gboard versions may not expose that validator and will receive an explicit
activation error. The default/system selection restores captured view styling and removes the
custom native theme setting.

## Use in Morphe

1. Add this repository as a Morphe source:
   `https://github.com/assc0d3r/ART-GLIDGE-CUSTOMIZATION`
2. Select Gboard in the Patcher and enable the desired customization patch.
3. Apply the patch and install the generated APK.
4. Configure the relevant option in Gboard Settings. For icon packs, open **Keyboard Icon Packs**
   and import the `.wmicons`/ZIP file; a successful import enables replacement for supported keys.

## Build

Run `build.sh` with JDK 21 and Bash. The script downloads the Kotlin compiler, Morphe CLI, and Android build
dependencies when they are not already cached in `libs/`.

The Morphe patch bundle is generated at `dist/patches-1.0.13.mpp`. Add the repository to Morphe
to consume the release bundle; use the `.mpp` file directly when sideloading a local build.
