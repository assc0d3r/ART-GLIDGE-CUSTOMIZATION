# Gboard Customizations

A Morphe patch bundle for Gboard (`com.google.android.inputmethod.latin`) containing:

- **Glide Trail Customization:** choose a solid trail color or rainbow effect, and adjust fade
  duration, width, and point retention.
- **Multilingual Suggestion:** optionally promote the first suggestion written in a different
  Unicode script beside Gboard's top candidate.
- **Keyboard Icon Packs:** import and export compatible WMKeyboard `.wmicons` ZIP packs and apply
  their icons to supported Gboard keys and toolbar buttons. Per-key icons can also be added in
  Theme Builder; selecting this patch also enables Theme Builder.
- **Gboard Theme Builder:** create native Gboard themes, import/export Gboard `.pack` and `.zip`
  packages plus WMKeyboard `.wmtheme.json`, and maintain deletable theme profiles. Imported native
  CSS sheets and border flavors are retained when compatible. The editor provides palette and
  per-key colors, shapes, textures, opacity, icon tint/custom icons, wallpaper colors, animation,
  toolbar/popup styling, TTF/OTF fonts for keyboard key labels, and sticker overlays.
- **AMOLED Gboard Settings:** apply black backgrounds and legible text/control colors to
  AndroidX preference rows. Because the hook targets the shared preference binder, settings
  added by other patches are styled automatically when Gboard displays them.

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
Theme Builder, avoiding a duplicate icon-pack category in Glide typing settings. The icon patch is
experimental: Gboard changes its internal key rendering between releases, so an unpinned target does
not guarantee every version will patch or expose every icon.

Theme Builder uses Gboard's format-version-3 native theme archive (`metadata.json`,
`style_sheet_variables.css`, and CSS stylesheets) as its activation/export format. It imports the
RBoard-style `.pack` wrapper with a nested native Gboard ZIP, direct native theme ZIPs, and WMKeyboard
`.wmtheme.json`. Theme JSON exports include sticker and per-key art assets; native Gboard ZIP
exports contain stylesheets and palette only. Compatible imported stylesheets and border flavors are retained and updated with
the editor's palette. Unknown format versions or missing declared stylesheets are rejected instead
of being silently converted. It supports up to 13 visual profiles and undo/redo. Gboard supplies
the actual key layout and actions; this builder only styles the rendered keys.
Imported icon packs are managed separately and are not embedded in theme exports.
Modifier-key and spacebar background/text colors can be edited separately, and the spacebar
is available in per-key styling. The key editor supports per-key color, shape, opacity, texture,
text styling, icon tint, and custom icons; custom icons take precedence over icon-pack assets.
Multiple sticker images can be imported together (up to six per theme). Theme JSON export/import
carries stickers, while native Gboard ZIP export cannot include the Morphe overlay stickers.
Custom TTF/OTF fonts can be applied to all rendered key labels or overridden per key. Theme JSON
exports carry font files; native Gboard ZIP exports do not embed them. The font hook does not
replace text elsewhere in Gboard, such as suggestions or settings, and Gboard renderer changes
may prevent it from applying.
Saved themes can be deleted from the Theme Builder theme list; deleting
the active theme returns Gboard to its system theme. The runtime depends on Gboard's internal
`SoftKeyView` render method and view-tree naming, so extended styling is experimental, may not cover
every surface, and is not guaranteed to work with every Gboard version. Native archive validation
depends on a validator
exposed by the installed Gboard build; unsupported builds receive an explicit activation error. The
patch and generated APK were smoke-tested against Gboard 18.5.6, but visual behavior still requires
device verification. The default/system selection restores captured view styling and removes the
custom native theme setting.

## Use in Morphe

1. Add this repository as a Morphe source:
   `https://github.com/assc0d3r/ART-GLIDGE-CUSTOMIZATION`
2. Select Gboard in the Patcher and enable the desired customization patch.
3. Apply the patch and install the generated APK.
4. Open **Gboard Theme Builder** from Gboard Settings. Import a `.pack`, native theme `.zip`, or
   `.wmtheme.json` to start from a compatible theme. Import and enable a `.wmicons` pack separately,
   or add icons to individual keys in the key editor.

## Build

Run `build.sh` with JDK 21 and Bash. The script downloads the Kotlin compiler, Morphe CLI, and Android build
dependencies when they are not already cached in `libs/`.

The Morphe patch bundle is generated at `dist/patches-1.0.25.mpp`. Add the repository to Morphe
to consume the release bundle; use the `.mpp` file directly when sideloading a local build.
