# Gboard Glide Trail and Multilingual Suggestions

A Morphe patch bundle for Gboard (`com.google.android.inputmethod.latin`) containing:

- **Glide Trail Customization:** choose a solid trail color or rainbow effect, and adjust fade
  duration, width, and point retention.
- **Multilingual Suggestion:** optionally promote the first suggestion written in a different
  Unicode script beside Gboard's top candidate.

The patches are exposed as experimental, version-unpinned Gboard targets. They still rely on
Gboard's internal classes and settings resources; versions that change those structures may not
be patchable.

The multilingual option is a best-effort heuristic, not language detection: it preserves Gboard's
first candidate and promotes the first candidate with a different writing script. It affects both
glide and ordinary suggestion rows, cannot distinguish languages that use the same script (such as
English and Hinglish), and does not rotate between language models.

## Use in Morphe

1. Add this repository as a Morphe source:
   `https://github.com/assc0d3r/ART-GLIDGE-CUSTOMIZATION`
2. Select Gboard in the Patcher and enable either **Glide Trail Customization** or
   **Multilingual Suggestion**.
3. Apply the patch and install the generated APK.
4. For multilingual suggestions, enable **Script-aware suggestions** in Gboard's Glide typing
   settings.

## Build

Run `build.sh` with JDK 21 and Bash. The script downloads the Kotlin compiler, Morphe CLI, and Android build
dependencies when they are not already cached in `libs/`.

The Morphe patch bundle is generated at `dist/patches-1.0.4.mpp`. Add the repository to Morphe
to consume the release bundle; use the `.mpp` file directly when sideloading a local build.
