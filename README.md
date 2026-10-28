# Gboard Glide Trail Customization

A standalone Morphe patch for Gboard (`com.google.android.inputmethod.latin`) that adds
customization controls for glide typing trails:

- Toggle custom behavior on or off.
- Choose a solid color with the HSV color wheel or enable the rainbow effect.
- Adjust trail fade duration, width, and point retention.

The patch is published as an experimental, version-unpinned Gboard target. Morphe can therefore
offer it for Gboard versions beyond a fixed allowlist. It still relies on Gboard's gesture overlay
and settings resources; versions that change those internal structures may not be patchable.

## Use in Morphe

1. Add this repository as a Morphe source:
   `https://github.com/assc0d3r/ART-GLIDGE-CUSTOMIZATION`
2. Select Gboard in the Patcher and enable **Glide Trail Customization**.
3. Apply the patch and install the generated APK.

## Build

Run `build.sh` with JDK 21 and Bash. The script downloads the Kotlin compiler, Morphe CLI, and Android build
dependencies when they are not already cached in `libs/`.

The Morphe patch bundle is generated at `dist/patches-1.0.2.mpp`. Add the repository to Morphe
to consume the release bundle; use the `.mpp` file directly when sideloading a local build.
