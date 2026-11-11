package dev.custom.gboardpatches.patches.extras

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val ICON_ANDROID_NS = "http://schemas.android.com/apk/res/android"
private const val ICON_SETTINGS_XML = "res/xml/setting_gesture.xml"
private const val ICON_MANIFEST = "AndroidManifest.xml"
private const val ICON_ACTIVITY = "dev.custom.gboardpatches.ui.KeyboardIconPackImportActivity"

val keyboardIconPackSettingsPatch = resourcePatch(
    description = "Registers the WMKeyboard icon-pack importer."
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

    execute {
        val settings = document(ICON_SETTINGS_XML)
        try {
            applyKeyboardIconPackSettings(settings)
        } finally {
            settings.close()
        }

        val manifest = document(ICON_MANIFEST)
        try {
            addKeyboardIconImportActivity(manifest)
            addExternalStoragePermission(manifest)
        } finally {
            manifest.close()
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
        val key = child.getAttributeNS(ICON_ANDROID_NS, "key")
        if (key == "pref_key_keyboard_icons_enabled" || key == "pref_key_keyboard_icon_pack_import") {
            parent.removeChild(child)
            continue
        }
        removeLegacyIconPackPreferences(child)
        if (
            child.tagName.endsWith("PreferenceCategory") &&
            child.getAttributeNS(ICON_ANDROID_NS, "title") == "Keyboard Icon Packs" &&
            !child.hasElementChildren()
        ) {
            parent.removeChild(child)
        }
    }
}

internal fun addKeyboardIconImportActivity(document: Document) {
    val application = document.getElementsByTagName("application").item(0) as? Element
        ?: error("AndroidManifest.xml application element not found.")

    val activities = application.getElementsByTagName("activity")
    val alreadyRegistered = (0 until activities.length).any { index ->
        val activity = activities.item(index) as? Element
        activity?.getAttributeNS(ICON_ANDROID_NS, "name") == ICON_ACTIVITY
    }
    if (alreadyRegistered) return

    application.appendChild(
        document.createElement("activity").apply {
            setAttributeNS(ICON_ANDROID_NS, "android:name", ICON_ACTIVITY)
            setAttributeNS(ICON_ANDROID_NS, "android:exported", "false")
            setAttributeNS(ICON_ANDROID_NS, "android:theme", "@android:style/Theme.Material.NoActionBar")
            setAttributeNS(ICON_ANDROID_NS, "android:excludeFromRecents", "true")
        }
    )
}

/**
 * The importer writes the selected pack to /sdcard/Gboard/icons/, which needs legacy storage
 * access. Gboard targets API 37, so the grant is limited to API 28 and below.
 */
internal fun addExternalStoragePermission(document: Document) {
    val manifest = document.documentElement ?: return
    val permissions = manifest.getElementsByTagName("uses-permission")
    for (index in 0 until permissions.length) {
        val permission = permissions.item(index) as? Element ?: continue
        if (
            permission.getAttributeNS(ICON_ANDROID_NS, "name") ==
            "android.permission.WRITE_EXTERNAL_STORAGE"
        ) {
            return
        }
    }

    val permission = document.createElement("uses-permission")
    permission.setAttributeNS(
        ICON_ANDROID_NS,
        "android:name",
        "android.permission.WRITE_EXTERNAL_STORAGE"
    )
    permission.setAttributeNS(ICON_ANDROID_NS, "android:maxSdkVersion", "28")
    manifest.insertBefore(permission, manifest.getElementsByTagName("application").item(0))
}

private fun Element.hasElementChildren(): Boolean {
    val children = childNodes
    for (index in 0 until children.length) {
        if (children.item(index) is Element) return true
    }
    return false
}
