package dev.custom.gboardpatches.patches.glidetrail

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val THEME_RUNTIME =
    "Ldev/custom/gboardpatches/ui/KeyboardThemeRuntime;"
private const val THEME_PREFERENCE_GROUP =
    "Landroidx/preference/PreferenceGroup;"

val gboardThemeStudioPatch = bytecodePatch(
    name = "Gboard Theme Studio",
    description = "Builds and activates Gboard-native ZIP themes from Theme Studio color and shape settings.",
    default = true
) {
    compatibleWith(
        Compatibility(
            name = "Gboard",
            packageName = "com.google.android.inputmethod.latin",
            targets = listOf(AppTarget(version = null, isExperimental = true))
        )
    )
    dependsOn(gboardThemeStudioSettingsPatch)
    extendWith("colorwheel.dex")

    execute {
        var preferenceHooks = 0
        classDefForEach { classDef ->
            val candidates = classDef.methods.filter { method ->
                if (
                    method.parameterTypes != listOf(THEME_PREFERENCE_GROUP) ||
                    method.returnType != "V"
                ) return@filter false
                val instructions = method.implementation?.instructions ?: return@filter false
                var hasCount = false
                var hasChild = false
                for (instruction in instructions) {
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: continue
                    if (reference.definingClass != THEME_PREFERENCE_GROUP) continue
                    if (reference.parameterTypes.isEmpty() && reference.returnType == "I") hasCount = true
                    if (
                        reference.parameterTypes == listOf("I") &&
                        reference.returnType == "Landroidx/preference/Preference;"
                    ) hasChild = true
                }
                hasCount && hasChild
            }
            if (candidates.isEmpty()) return@classDefForEach
            val mutableClass = mutableClassDefBy(classDef)
            candidates.forEach { candidate ->
                val method = mutableClass.methods.firstOrNull {
                    it.name == candidate.name && it.parameterTypes == candidate.parameterTypes
                } ?: return@forEach
                method.addInstructions(
                    0,
                    """
                    move-object/from16 v0, p1
                    invoke-static {v0}, $THEME_RUNTIME->initializePreferences(Ljava/lang/Object;)V
                    """.trimIndent()
                )
                preferenceHooks++
            }
        }
        if (preferenceHooks == 0) error("Gboard preference-group initialization method not found")
    }
}
