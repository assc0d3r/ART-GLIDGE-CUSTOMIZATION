package dev.custom.gboardpatches.patches.glidetrail

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val THEME_RUNTIME =
    "Ldev/custom/gboardpatches/ui/KeyboardThemeRuntime;"
private const val ICON_RUNTIME =
    "Ldev/custom/gboardpatches/ui/KeyboardIconPackRuntime;"
private const val THEME_PREFERENCE_GROUP =
    "Landroidx/preference/PreferenceGroup;"
private const val SOFT_KEY_VIEW =
    "Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;"
private const val SOFT_KEY_DEF =
    "Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;"

private fun isVoidReturn(opcode: Opcode): Boolean =
    opcode == Opcode.RETURN_VOID ||
        opcode == Opcode.RETURN_VOID_BARRIER ||
        opcode == Opcode.RETURN_VOID_NO_BARRIER

val gboardThemeStudioPatch = bytecodePatch(
    name = "Gboard Theme Builder",
    description = "Create and export native Gboard themes, customize per-key visuals, and manage sticker artwork.",
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

        val softKeyView = mutableClassDefByOrNull(SOFT_KEY_VIEW)
            ?: error("Gboard SoftKeyView class not found")
        val renderKeys = softKeyView.methods.firstOrNull {
            it.name == "A" &&
                it.parameterTypes == listOf(SOFT_KEY_DEF) &&
                it.returnType == "V"
        } ?: error("Gboard SoftKeyView render method not found")
        val instructions = renderKeys.implementation?.instructions?.toList()
            ?: error("Gboard SoftKeyView render method has no implementation")
        val iconHookAlreadyPresent = instructions.any { instruction ->
            val methodReference =
                (instruction as? ReferenceInstruction)?.reference as? MethodReference
            methodReference?.definingClass == ICON_RUNTIME &&
                methodReference.name == "applyIcon"
        }
        val imageViewId = if (iconHookAlreadyPresent) 0 else {
            instructions.indices.firstNotNullOfOrNull { index ->
                val methodReference =
                    (instructions[index] as? ReferenceInstruction)?.reference as? MethodReference
                        ?: return@firstNotNullOfOrNull null
                if (
                    instructions[index].opcode != Opcode.INVOKE_VIRTUAL ||
                    methodReference.definingClass != softKeyView.type ||
                    methodReference.parameterTypes != listOf("I") ||
                    methodReference.returnType != "Landroid/view/View;"
                ) return@firstNotNullOfOrNull null

                val castsToImageView = instructions.drop(index + 1).take(5).any { instruction ->
                    instruction.opcode == Opcode.CHECK_CAST &&
                        (instruction as? ReferenceInstruction)?.reference?.toString() ==
                        "Landroid/widget/ImageView;"
                }
                if (!castsToImageView) return@firstNotNullOfOrNull null
                (instructions.getOrNull(index - 1) as? NarrowLiteralInstruction)?.narrowLiteral
            } ?: 0
        }
        val keyReturns = instructions
            .withIndex()
            .filter { isVoidReturn(it.value.opcode) }
            .map { it.index }
        if (keyReturns.isEmpty()) error("Gboard SoftKeyView render method has no return")
        keyReturns.asReversed().forEach { returnIndex ->
            val iconHook = if (iconHookAlreadyPresent) "" else """
                const v2, $imageViewId
                invoke-static {v0, v1, v2}, $ICON_RUNTIME->applyIcon(Landroid/view/View;Ljava/lang/Object;I)V
            """.trimIndent()
            renderKeys.addInstructions(
                returnIndex,
                """
                move-object/from16 v0, p0
                move-object/from16 v1, p1
                invoke-static {v0, v1}, $THEME_RUNTIME->applyKey(Landroid/view/View;Ljava/lang/Object;)V
                $iconHook
                """.trimIndent()
            )
        }
    }

}
