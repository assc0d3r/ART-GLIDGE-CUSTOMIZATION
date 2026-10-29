package dev.custom.gboardpatches.patches.glidetrail

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val THEME_SOFT_KEY_VIEW =
    "Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;"
private const val THEME_SOFT_KEYBOARD_VIEW =
    "Lcom/google/android/libraries/inputmethod/widgets/SoftKeyboardView;"
private const val THEME_RUNTIME =
    "Ldev/custom/gboardpatches/ui/KeyboardThemeRuntime;"
private const val THEME_PREFERENCE_GROUP =
    "Landroidx/preference/PreferenceGroup;"

val gboardThemeStudioPatch = bytecodePatch(
    name = "Gboard Theme Studio",
    description = "Adds an editable, importable theme engine for Gboard key colors, shapes, looks, and board backgrounds.",
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
        val keyView = mutableClassDefByOrNull(THEME_SOFT_KEY_VIEW)
            ?: error("Gboard SoftKeyView class not found")
        val bindMethod = keyView.methods.firstOrNull { method ->
            method.name == "A" &&
                method.parameterTypes == listOf("Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;") &&
                method.returnType == "V"
        } ?: error("Gboard key-binding method not found")
        val bindImplementation = bindMethod.implementation
            ?: error("Gboard key-binding method has no implementation")
        val bindReturns = bindImplementation.instructions.withIndex()
            .filter { it.value.opcode == Opcode.RETURN_VOID }
            .map { it.index }
        if (bindReturns.isEmpty()) error("Gboard key-binding method has no return instruction")
        val applyKey = """
            move-object/from16 v0, p0
            move-object/from16 v1, p1
            invoke-static {v0, v1}, $THEME_RUNTIME->applyKey(Landroid/view/View;Ljava/lang/Object;)V
            """.trimIndent()
        bindReturns.asReversed().forEach { bindMethod.addInstructions(it, applyKey) }

        val keyboardView = mutableClassDefByOrNull(THEME_SOFT_KEYBOARD_VIEW)
            ?: error("Gboard SoftKeyboardView class not found")
        val inflateMethod = keyboardView.methods.firstOrNull { method ->
            method.name == "onFinishInflate" &&
                method.parameterTypes.isEmpty() &&
                method.returnType == "V"
        } ?: error("Gboard keyboard background initialization method not found")
        val inflateReturns = inflateMethod.implementation?.instructions?.withIndex()
            ?.filter { it.value.opcode == Opcode.RETURN_VOID }
            ?.map { it.index }
            ?: error("Gboard keyboard background method has no implementation")
        if (inflateReturns.isEmpty()) error("Gboard keyboard background method has no return instruction")
        inflateReturns.asReversed().forEach { index ->
            inflateMethod.addInstructions(
                index,
                """
                move-object/from16 v0, p0
                invoke-static {v0}, $THEME_RUNTIME->applyBoard(Landroid/view/View;)V
                """.trimIndent()
            )
        }

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
