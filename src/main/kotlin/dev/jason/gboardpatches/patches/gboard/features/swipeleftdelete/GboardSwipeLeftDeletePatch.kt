package dev.jason.gboardpatches.patches.gboard.features.swipeleftdelete

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch

val gboardSwipeLeftDeletePatch = bytecodePatch(
    name = "Swipe Left to Delete",
    description = "向左滑動鍵盤即為退格鍵，滑動時逐字刪除，持續按住則改為逐詞刪除。\n" +
        "Sliding left anywhere on the keyboard acts as backspace and deletes characters while " +
        "you drag; keep holding to delete whole words.",
    default = true,
) {
    compatibleWith(
        Compatibility(
            name = "Gboard",
            packageName = "com.google.android.inputmethod.latin",
            targets = listOf(
                AppTarget("18.6.3.1002251336-release-arm64-v8a"),
                // The pointer family the feature rides on was audited on 18.8.0: the owner moved
                // from Lqck;->B to Lagyw;->D and the update-target method from F to H.
                AppTarget("18.8.0.1022711984-beta-arm64-v8a"),
            ),
        ),
    )
    dependsOn(
        gboardPatchesExtensionCarrierPatch,
        gboardSwipeLeftDeletePointerPatch,
    )
    extendWith("colorwheel.dex")
}
