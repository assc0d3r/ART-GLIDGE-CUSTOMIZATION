package dev.custom.gboardpatches.patches.glidetrail

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val KEYBOARD_VIEW =
    "Lcom/google/android/libraries/inputmethod/widgets/SoftKeyboardView;"
private const val SOFT_KEY_VIEW =
    "Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;"
private const val SOFT_KEY_DEFINITION =
    "Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;"
private const val MOTION_EVENT = "Landroid/view/MotionEvent;"
private const val GESTURE_RUNTIME =
    "Ldev/custom/gboardpatches/ui/KeyboardGestureRuntime;"

val keyboardGesturePatch = bytecodePatch(
    name = "XGesture Typing Controls",
    description = "Adds spacebar cursor controls and C/P/U/R gesture drawing, delete-key gestures, and customizable snippets and macros.",
    default = true
) {
    compatibleWith(
        Compatibility(
            name = "Gboard",
            packageName = "com.google.android.inputmethod.latin",
            targets = listOf(AppTarget(version = null, isExperimental = true))
        )
    )
    dependsOn(keyboardGestureSettingsPatch)
    extendWith("colorwheel.dex")

    execute {
        val keyboardView = mutableClassDefByOrNull(KEYBOARD_VIEW)
            ?: error("Gboard SoftKeyboardView class not found; XGesture cannot be installed.")
        val parentClass = keyboardView.superclass
            ?: error("Gboard SoftKeyboardView superclass could not be identified.")

        val interceptMethod = keyboardView.methods.firstOrNull {
            it.name == "onInterceptTouchEvent" &&
                it.parameterTypes == listOf(MOTION_EVENT) &&
                it.returnType == "Z"
        }
        if (interceptMethod == null) {
            keyboardView.methods.add(
                createTouchOverride(
                    KEYBOARD_VIEW,
                    parentClass,
                    "onInterceptTouchEvent",
                    AccessFlags.PROTECTED.value,
                    "onInterceptTouchEvent"
                )
            )
        } else {
            interceptMethod.addInstructions(
                0,
                """
                invoke-static {p0, p1}, $GESTURE_RUNTIME->onInterceptTouchEvent(Landroid/view/View;Landroid/view/MotionEvent;)Z
                move-result v0
                if-eqz v0, :morphe_xgesture_intercept_continue
                const/4 v0, 0x1
                return v0
                :morphe_xgesture_intercept_continue
                """.trimIndent()
            )
        }

        val touchMethod = keyboardView.methods.firstOrNull {
            it.name == "onTouchEvent" &&
                it.parameterTypes == listOf(MOTION_EVENT) &&
                it.returnType == "Z"
        }
        if (touchMethod == null) {
            keyboardView.methods.add(
                createTouchOverride(
                    KEYBOARD_VIEW,
                    parentClass,
                    "onTouchEvent",
                    AccessFlags.PUBLIC.value,
                    "consumeInterceptedTouch"
                )
            )
        } else {
            touchMethod.addInstructions(
                0,
                """
                invoke-static {p0, p1}, $GESTURE_RUNTIME->consumeInterceptedTouch(Landroid/view/View;Landroid/view/MotionEvent;)Z
                move-result v0
                if-eqz v0, :morphe_xgesture_touch_continue
                const/4 v0, 0x1
                return v0
                :morphe_xgesture_touch_continue
                """.trimIndent()
            )
        }

        val softKeyView = mutableClassDefByOrNull(SOFT_KEY_VIEW)
            ?: error("Gboard SoftKeyView class not found; XGesture key gestures cannot be installed.")
        val keyRenderMethod = softKeyView.methods.firstOrNull {
            it.name == "A" &&
                it.parameterTypes == listOf(SOFT_KEY_DEFINITION) &&
                it.returnType == "V"
        } ?: error("Gboard SoftKeyView render method not found; XGesture cannot identify space/delete keys.")
        if (keyRenderMethod.implementation == null) {
            error("Gboard SoftKeyView render method has no implementation.")
        }
        keyRenderMethod.addInstructions(
            0,
            """
            move-object/from16 v0, p0
            move-object/from16 v1, p1
            invoke-static {v0, v1}, $GESTURE_RUNTIME->registerRenderedKey(Landroid/view/View;Ljava/lang/Object;)V
            """.trimIndent()
        )
    }
}

private fun createTouchOverride(
    classType: String,
    parentClass: String,
    methodName: String,
    accessFlags: Int,
    runtimeMethodName: String
) = ImmutableMethod(
    classType,
    methodName,
    listOf(ImmutableMethodParameter(MOTION_EVENT, emptySet(), null)),
    "Z",
    accessFlags,
    null,
    null,
    MutableMethodImplementation(3)
).toMutable().apply {
    addInstructions(
        0,
        """
        invoke-static {p0, p1}, $GESTURE_RUNTIME->$runtimeMethodName(Landroid/view/View;Landroid/view/MotionEvent;)Z
        move-result v0
        if-nez v0, :morphe_xgesture_handled
        invoke-super {p0, p1}, $parentClass->$methodName($MOTION_EVENT)Z
        move-result v0
        return v0
        :morphe_xgesture_handled
        const/4 v0, 0x1
        return v0
        """.trimIndent()
    )
}
