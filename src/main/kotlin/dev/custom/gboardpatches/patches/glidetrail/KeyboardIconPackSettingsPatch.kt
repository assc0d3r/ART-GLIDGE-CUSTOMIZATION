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

const val PREF_KEY_KEYBOARD_ICONS_ENABLED = "pref_key_keyboard_icons_enabled"
const val PREF_KEY_KEYBOARD_ICON_PACK_IMPORT = "pref_key_keyboard_icon_pack_import"

val keyboardIconPackSettingsPatch = resourcePatch(
    description = "Adds WMKeyboard icon-pack import and enable controls to Gboard Settings."
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
    if (root.findPreference(PREF_KEY_KEYBOARD_ICON_PACK_IMPORT) != null) {
        root.findPreference(PREF_KEY_KEYBOARD_ICONS_ENABLED)
            ?.setAttributeNS(KEYBOARD_ICON_ANDROID_NS, "android:persistent", "true")
        return
    }

    val category = document.createElement("androidx.preference.PreferenceCategory").apply {
        setAttributeNS(KEYBOARD_ICON_ANDROID_NS, "android:title", "Keyboard Icon Packs")
    }
    category.appendChild(
        document.createElement("SwitchPreferenceCompat").apply {
            setAttributeNS(KEYBOARD_ICON_ANDROID_NS, "android:persistent", "true")
            setAttributeNS(KEYBOARD_ICON_ANDROID_NS, "android:title", "Use imported keyboard icons")
            setAttributeNS(
                KEYBOARD_ICON_ANDROID_NS,
                "android:summary",
                "Replace matching Gboard key and toolbar icons from the imported pack",
            )
            setAttributeNS(KEYBOARD_ICON_ANDROID_NS, "android:key", PREF_KEY_KEYBOARD_ICONS_ENABLED)
            setAttributeNS(KEYBOARD_ICON_ANDROID_NS, "android:defaultValue", "false")
        }
    )
    category.appendChild(
        document.createElement("Preference").apply {
            setAttributeNS(KEYBOARD_ICON_ANDROID_NS, "android:persistent", "false")
            setAttributeNS(KEYBOARD_ICON_ANDROID_NS, "android:title", "Import WMKeyboard icon pack")
            setAttributeNS(
                KEYBOARD_ICON_ANDROID_NS,
                "android:summary",
                "Choose a .wmicons or ZIP file. Icons without a matching Gboard key or button are ignored.",
            )
            setAttributeNS(KEYBOARD_ICON_ANDROID_NS, "android:key", PREF_KEY_KEYBOARD_ICON_PACK_IMPORT)
        }
    )
    root.appendChild(category)
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

private fun Element.findPreference(targetKey: String): Element? {
    if (getAttributeNS(KEYBOARD_ICON_ANDROID_NS, "key") == targetKey) return this
    val children = childNodes
    for (index in 0 until children.length) {
        val child = children.item(index) as? Element ?: continue
        child.findPreference(targetKey)?.let { return it }
    }
    return null
}
