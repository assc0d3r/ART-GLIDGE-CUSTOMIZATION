package dev.custom.gboardpatches.patches.extras

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private const val PREFERENCE_CLASS = "Landroidx/preference/Preference;"
private const val AMOLED_RUNTIME = "Ldev/custom/gboardpatches/ui/AmoledSettingsRuntime;"

/**
 * Applies a pure-black theme to Gboard settings rows.
 *
 * In 18.6.3 `PreferenceViewHolder` is R8-renamed to `Lcew;` and its `itemView` field along
 * with it, so the hook is pinned to the verified `Preference.a(Lcew;)V` bind method and the
 * runtime locates the row view by field type instead of by name.
 */
val amoledSettingsPatch = bytecodePatch(
    name = "AMOLED Gboard Settings",
    description = "Applies a pure-black theme to Gboard settings rows and future patch preferences.",
    default = true
) {
    compatibleWith(
        Compatibility(
            name = "Gboard",
            packageName = "com.google.android.inputmethod.latin",
            targets = listOf(
                AppTarget(version = "18.6.3.1002251336-release-arm64-v8a"),
                AppTarget(version = null, isExperimental = true)
            )
        )
    )
    extendWith("colorwheel.dex")

    execute {
        val preferenceClass = mutableClassDefByOrNull(PREFERENCE_CLASS)
            ?: error("AndroidX Preference class not found in Gboard.")

        val bindMethod = preferenceClass.methods.firstOrNull { method ->
            method.name == "a" &&
                method.returnType == "V" &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == "Lcew;"
        } ?: error("Gboard preference row-binding method a(Lcew;)V not found.")

        val implementation = bindMethod.implementation
            ?: error("Gboard preference row-binding method has no implementation.")

        val returnIndexes = implementation.instructions
            .withIndex()
            .filter { it.value.opcode == Opcode.RETURN_VOID }
            .map { it.index }
        if (returnIndexes.isEmpty()) error("Gboard preference row-binding method has no return.")

        returnIndexes.asReversed().forEach { returnIndex ->
            bindMethod.addInstructions(
                returnIndex,
                """
                invoke-static {p0, p1}, $AMOLED_RUNTIME->applyPreference(Ljava/lang/Object;Ljava/lang/Object;)V
                """.trimIndent()
            )
        }
    }
}
