package dev.custom.gboardpatches.patches.glidetrail

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private const val PREFERENCE =
    "Landroidx/preference/Preference;"
private const val PREFERENCE_VIEW_HOLDER =
    "Landroidx/preference/PreferenceViewHolder;"
private const val AMOLED_RUNTIME =
    "Ldev/custom/gboardpatches/ui/AmoledSettingsRuntime;"

val amoledSettingsPatch = bytecodePatch(
    name = "AMOLED Gboard Settings",
    description = "Applies a pure-black theme to Gboard settings rows and future patch preferences.",
    default = true
) {
    compatibleWith(
        Compatibility(
            name = "Gboard",
            packageName = "com.google.android.inputmethod.latin",
            targets = listOf(AppTarget(version = null, isExperimental = true))
        )
    )
    extendWith("colorwheel.dex")

    execute {
        val preferenceClass = mutableClassDefByOrNull(PREFERENCE)
            ?: error("AndroidX Preference class not found in Gboard.")
        val bindMethod = preferenceClass.methods.firstOrNull {
            it.name == "onBindViewHolder" &&
                it.parameterTypes == listOf(PREFERENCE_VIEW_HOLDER) &&
                it.returnType == "V"
        } ?: preferenceClass.methods.firstOrNull {
            it.parameterTypes.size == 1 &&
                it.parameterTypes[0].endsWith("/PreferenceViewHolder;") &&
                it.returnType == "V"
        } ?: error(
            "AndroidX Preference row-binding method not found in Gboard. " +
                "The installed Preference implementation may be incompatible."
        )
        val returnIndexes = bindMethod.implementation?.instructions
            ?.withIndex()
            ?.filter { it.value.opcode == Opcode.RETURN_VOID }
            ?.map { it.index }
            ?: error("AndroidX Preference row-binding method has no implementation.")
        if (returnIndexes.isEmpty()) error("AndroidX Preference row-binding method has no return.")

        returnIndexes.asReversed().forEach { returnIndex ->
            bindMethod.addInstructions(
                returnIndex,
                """
                move-object/from16 v0, p0
                move-object/from16 v1, p1
                invoke-static {v0, v1}, $AMOLED_RUNTIME->applyPreference(Ljava/lang/Object;Ljava/lang/Object;)V
                """.trimIndent()
            )
        }
    }
}
