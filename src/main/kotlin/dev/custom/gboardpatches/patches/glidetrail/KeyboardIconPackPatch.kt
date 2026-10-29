package dev.custom.gboardpatches.patches.glidetrail

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val SOFT_KEY_VIEW =
    "Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;"
private const val ICON_RUNTIME =
    "Ldev/custom/gboardpatches/ui/KeyboardIconPackRuntime;"
private const val PREFERENCE_GROUP =
    "Landroidx/preference/PreferenceGroup;"

val keyboardIconPackPatch = bytecodePatch(
    name = "Keyboard Icon Packs",
    description = "Imports WMKeyboard icon packs and applies matching icons to Gboard keys and toolbar buttons.",
    default = true
) {
    compatibleWith(
        Compatibility(
            name = "Gboard",
            packageName = "com.google.android.inputmethod.latin",
            targets = listOf(AppTarget(version = null, isExperimental = true))
        )
    )
    dependsOn(keyboardIconPackSettingsPatch)
    extendWith("colorwheel.dex")

    execute {
        val softKeyView = mutableClassDefByOrNull(SOFT_KEY_VIEW)
            ?: error("Gboard SoftKeyView class not found")
        val renderIcons = softKeyView.methods.firstOrNull { method ->
            method.name == "A" &&
                method.parameterTypes == listOf("Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;") &&
                method.returnType == "V"
        } ?: error("Gboard SoftKeyView icon-render method not found")
        val implementation = renderIcons.implementation
            ?: error("Gboard SoftKeyView icon-render method has no implementation")
        val instructions = implementation.instructions.toList()

        val imageViewId = instructions.indices.firstNotNullOfOrNull { index ->
            val methodReference =
                (instructions[index] as? ReferenceInstruction)?.reference as? MethodReference
                    ?: return@firstNotNullOfOrNull null
            if (
                instructions[index].opcode != Opcode.INVOKE_VIRTUAL ||
                methodReference.definingClass != softKeyView.type ||
                methodReference.parameterTypes != listOf("I") ||
                methodReference.returnType != "Landroid/view/View;"
            ) {
                return@firstNotNullOfOrNull null
            }

            val castsToImageView = instructions
                .drop(index + 1)
                .take(5)
                .any { instruction ->
                    instruction.opcode == Opcode.CHECK_CAST &&
                        (instruction as? ReferenceInstruction)?.reference?.toString() ==
                        "Landroid/widget/ImageView;"
                }
            if (!castsToImageView) return@firstNotNullOfOrNull null

            (instructions.getOrNull(index - 1) as? NarrowLiteralInstruction)?.narrowLiteral
        } ?: error("Gboard key icon view could not be identified")

        val returnIndexes = instructions.indices.filter {
            instructions[it].opcode == Opcode.RETURN_VOID
        }
        if (returnIndexes.isEmpty()) error("Gboard SoftKeyView icon-render method has no return")
        val applyIconInstructions = """
            move-object/from16 v0, p0
            move-object/from16 v1, p1
            const v2, $imageViewId
            invoke-static {v0, v1, v2}, $ICON_RUNTIME->applyIcon(Landroid/view/View;Ljava/lang/Object;I)V
            """.trimIndent()
        returnIndexes.asReversed().forEach { returnIndex ->
            renderIcons.addInstructions(returnIndex, applyIconInstructions)
        }

        var preferenceHooks = 0
        classDefForEach { classDef ->
            val matchingMethods = classDef.methods.filter { method ->
                if (
                    method.parameterTypes != listOf(PREFERENCE_GROUP) ||
                    method.returnType != "V"
                ) {
                    return@filter false
                }
                val methodInstructions = method.implementation?.instructions ?: return@filter false
                var readsGroupCount = false
                var readsGroupChildren = false
                for (instruction in methodInstructions) {
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: continue
                    if (reference.definingClass != PREFERENCE_GROUP) continue
                    if (reference.parameterTypes.isEmpty() && reference.returnType == "I") {
                        readsGroupCount = true
                    }
                    if (
                        reference.parameterTypes == listOf("I") &&
                        reference.returnType == "Landroidx/preference/Preference;"
                    ) {
                        readsGroupChildren = true
                    }
                }
                readsGroupCount && readsGroupChildren
            }
            if (matchingMethods.isEmpty()) return@classDefForEach

            val mutableClass = mutableClassDefBy(classDef)
            for (method in matchingMethods) {
                val mutableMethod = mutableClass.methods.firstOrNull {
                    it.name == method.name && it.parameterTypes == method.parameterTypes
                } ?: continue
                mutableMethod.addInstructions(
                    0,
                    """
                    move-object/from16 v0, p1
                    invoke-static {v0}, $ICON_RUNTIME->initializePreferences(Ljava/lang/Object;)V
                    """.trimIndent()
                )
                preferenceHooks++
            }
        }
        if (preferenceHooks == 0) {
            error("Gboard preference-group initialization method not found")
        }
    }
}
