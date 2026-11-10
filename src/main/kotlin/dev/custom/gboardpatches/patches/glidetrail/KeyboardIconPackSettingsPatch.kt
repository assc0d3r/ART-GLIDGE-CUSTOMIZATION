package dev.custom.gboardpatches.patches.glidetrail

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val KEYBOARD_ICON_ANDROID_NS = "http://schemas.android.com/apk/res/android"
private const val KEYBOARD_ICON_SETTINGS_XML = "res/xml/setting_gesture.xml"
private const val KEYBOARD_ICON_MANIFEST = "AndroidManifest.xml"
private const val KEYBOARD_ICON_ACTIVITY =
    "dev.custom.gboardpatches.ui.KeyboardIconPackImportActivity"

val keyboardIconPackSettingsPatch = resourcePatch(
    description = "Registers the WMKeyboard icon-pack importer without duplicating Theme Studio controls."
) {
    compatibleWith(
        Compatibility(
            name = "Gboard",
            packageName = "com.google.android.inputmethod.latin",
            targets = listOf(AppTarget(version = null, isExperimental = true))
        )
    )

    execute {
        val settingsDocument = document(KEYBOARD_ICON_SETTINGS_XML)
        try {
            applyKeyboardIconPackSettings(settingsDocument)
        } finally {
            settingsDocument.close()
        }

        val manifestDocument = document(KEYBOARD_ICON_MANIFEST)
        try {
            addKeyboardIconImportActivity(manifestDocument)
        } finally {
            manifestDocument.close()
        }
    }
}

internal fun applyKeyboardIconPackSettings(document: Document) {
    val root = document.documentElement ?: return
    removeLegacyIconPackPreferences(root)
}

private fun removeLegacyIconPackPreferences(parent: Element) {
    val children = parent.childNodes
    for (index in children.length - 1 downTo 0) {
        val child = children.item(index) as? Element ?: continue
        val key = child.getAttributeNS(KEYBOARD_ICON_ANDROID_NS, "key")
        if (
            key == "pref_key_keyboard_icons_enabled" ||
            key == "pref_key_keyboard_icon_pack_import"
        ) {
            parent.removeChild(child)
            continue
        }
        removeLegacyIconPackPreferences(child)
        if (
            child.tagName.endsWith("PreferenceCategory") &&
            child.getAttributeNS(KEYBOARD_ICON_ANDROID_NS, "title") == "Keyboard Icon Packs" &&
            !child.hasElementChildren()
        ) {
            parent.removeChild(child)
        }
    }
}

internal fun addKeyboardIconImportActivity(document: Document) {
    val application = document.getElementsByTagName("application").item(0) as? Element
        ?: error("AndroidManifest.xml application element not found")
    val alreadyRegistered = application.getElementsByTagName("activity").let { activities ->
        (0 until activities.length).any { index ->
            val activity = activities.item(index) as? Element
            activity?.getAttributeNS(KEYBOARD_ICON_ANDROID_NS, "name") == KEYBOARD_ICON_ACTIVITY
        }
    }
    if (alreadyRegistered) return

    application.appendChild(
        document.createElement("activity").apply {
            setAttributeNS(KEYBOARD_ICON_ANDROID_NS, "android:name", KEYBOARD_ICON_ACTIVITY)
            setAttributeNS(KEYBOARD_ICON_ANDROID_NS, "android:exported", "false")
            setAttributeNS(
                KEYBOARD_ICON_ANDROID_NS,
                "android:theme",
                "@android:style/Theme.Translucent.NoTitleBar",
            )
            setAttributeNS(KEYBOARD_ICON_ANDROID_NS, "android:excludeFromRecents", "true")
        }
    )
}

private fun Element.hasElementChildren(): Boolean {
    val children = childNodes
    for (index in 0 until children.length) {
        if (children.item(index) is Element) return true
    }
    return false
}
