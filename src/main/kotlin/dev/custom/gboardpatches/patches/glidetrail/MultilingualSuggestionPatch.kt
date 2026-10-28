package dev.custom.gboardpatches.patches.glidetrail

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

private const val CANDIDATE_HOLDER_CLASS =
    "Lcom/google/android/apps/inputmethod/latin/keyboard/widget/LatinFixedCountCandidatesHolderView;"
private const val CANDIDATE_LIST_PARAMETER = "Ljava/util/List;"
private const val ARRAY_LIST_PARAMETER = "Ljava/util/ArrayList;"
private const val HELPER_CLASS = "Ldev/custom/gboardpatches/ui/MultilingualSuggestionHelper;"

val multilingualSuggestionPatch = bytecodePatch(
    name = "Multilingual Suggestion",
    description = "Promotes glide and typing suggestions written in a different script.",
    default = true
) {
    compatibleWith(
        Compatibility(
            name = "Gboard",
            packageName = "com.google.android.inputmethod.latin",
            targets = listOf(AppTarget(version = null, isExperimental = true))
        )
    )
    dependsOn(multilingualSuggestionSettingsPatch)
    extendWith("colorwheel.dex")

    execute {
        val holderClass = mutableClassDefBy(CANDIDATE_HOLDER_CLASS)
        val updateCandidatesMethod = holderClass.methods.firstOrNull { method ->
            method.name == "D" &&
                method.parameterTypes == listOf(CANDIDATE_LIST_PARAMETER, ARRAY_LIST_PARAMETER) &&
                method.returnType == "V"
        } ?: error("Gboard candidate-row update method not found")

        updateCandidatesMethod.addInstructions(
            0,
            """
            move-object/from16 v0, p0
            invoke-virtual {v0}, Landroid/view/View;->getContext()Landroid/content/Context;
            move-result-object v1
            move-object/from16 v2, p1
            invoke-static {v1, v2}, $HELPER_CLASS->reorderCandidates(Landroid/content/Context;Ljava/util/List;)Ljava/util/List;
            move-result-object v1
            move-object/16 p1, v1
            """.trimIndent()
        )
    }
}
