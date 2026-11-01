package dev.custom.gboardpatches.patches.glidetrail

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Document
import org.w3c.dom.Element

const val PREF_KEY_XGESTURE_KEYBOARD_ENABLED = "pref_key_xgesture_keyboard_enabled"
const val PREF_KEY_XGESTURE_GESTURE_DRAWING = "pref_key_xgesture_gesture_drawing"

private const val XGESTURE_ANDROID_NS = "http://schemas.android.com/apk/res/android"
private const val XGESTURE_SETTINGS_XML = "res/xml/setting_gesture.xml"
private const val XGESTURE_CATEGORY_TITLE = "XGesture Typing Controls"

val keyboardGestureSettingsPatch = resourcePatch(
    description = "Adds opt-in spacebar, delete-key, gesture-drawing, snippet, and macro controls."
) {
    compatibleWith(
        Compatibility(
            name = "Gboard",
            packageName = "com.google.android.inputmethod.latin",
            targets = listOf(AppTarget(version = null, isExperimental = true))
        )
    )

    execute {
        val settings = document(XGESTURE_SETTINGS_XML)
        try {
            addKeyboardGesturePreference(settings)
        } finally {
            settings.close()
        }
    }
}

private fun addKeyboardGesturePreference(document: Document) {
    val root = document.documentElement
        ?: error("Gboard gesture settings XML has no root element.")
    val category = findGestureCategory(root) ?: document.createElement(
        "androidx.preference.PreferenceCategory"
    ).apply {
        setAttributeNS(XGESTURE_ANDROID_NS, "android:title", XGESTURE_CATEGORY_TITLE)
        root.appendChild(this)
    }

    addSwitchPreference(
        document,
        category,
        PREF_KEY_XGESTURE_KEYBOARD_ENABLED,
        "Enable keyboard-local gestures",
        "Hold-drag the spacebar to move the cursor; two-finger drag selects text. Swipe the delete key left for a word or up for a sentence; double-tap for undo and hold for faster deletion. Keyboard-edge swipes and bottom-corner editing tools are also available. Disabled by default.",
        "false",
    )
    addSwitchPreference(
        document,
        category,
        PREF_KEY_XGESTURE_GESTURE_DRAWING,
        "Enable spacebar gesture drawing",
        "After holding the spacebar, draw C to copy, P to paste, U to undo, or R to redo. Unrecognized strokes move the cursor instead.",
        "true",
    )
}

private fun findGestureCategory(parent: Element): Element? {
    if (
        parent.tagName.endsWith("PreferenceCategory") &&
        parent.getAttributeNS(XGESTURE_ANDROID_NS, "title") == XGESTURE_CATEGORY_TITLE
    ) return parent
    val children = parent.childNodes
    for (index in 0 until children.length) {
        val child = children.item(index) as? Element ?: continue
        findGestureCategory(child)?.let { return it }
    }
    return null
}

private fun addSwitchPreference(
    document: Document,
    category: Element,
    key: String,
    title: String,
    summary: String,
    defaultValue: String,
) {
    if (document.documentElement.hasPreferenceKey(key)) return
    category.appendChild(
        document.createElement("SwitchPreferenceCompat").apply {
            setAttributeNS(XGESTURE_ANDROID_NS, "android:persistent", "true")
            setAttributeNS(XGESTURE_ANDROID_NS, "android:title", title)
            setAttributeNS(XGESTURE_ANDROID_NS, "android:summary", summary)
            setAttributeNS(XGESTURE_ANDROID_NS, "android:key", key)
            setAttributeNS(XGESTURE_ANDROID_NS, "android:defaultValue", defaultValue)
        }
    )
}

private fun Element.hasPreferenceKey(key: String): Boolean {
    if (getAttributeNS(XGESTURE_ANDROID_NS, "key") == key) return true
    val children = childNodes
    for (index in 0 until children.length) {
        val child = children.item(index) as? Element ?: continue
        if (child.hasPreferenceKey(key)) return true
    }
    return false
}
