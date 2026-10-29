package dev.custom.gboardpatches.patches.glidetrail

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val THEME_STUDIO_ANDROID_NS = "http://schemas.android.com/apk/res/android"
private const val THEME_STUDIO_SETTINGS_XML = "res/xml/setting_gesture.xml"
private const val THEME_STUDIO_MANIFEST = "AndroidManifest.xml"
private const val THEME_STUDIO_ACTIVITY =
    "dev.custom.gboardpatches.ui.KeyboardThemeStudioActivity"

val gboardThemeStudioSettingsPatch = resourcePatch(
    description = "Adds the Gboard Theme Studio entry and activity."
) {
    compatibleWith(
        Compatibility(
            name = "Gboard",
            packageName = "com.google.android.inputmethod.latin",
            targets = listOf(AppTarget(version = null, isExperimental = true))
        )
    )

    execute {
        val settings = document(THEME_STUDIO_SETTINGS_XML)
        try {
            addThemeStudioPreference(settings)
        } finally {
            settings.close()
        }

        val manifest = document(THEME_STUDIO_MANIFEST)
        try {
            addThemeStudioActivity(manifest)
        } finally {
            manifest.close()
        }
    }
}

private fun addThemeStudioPreference(document: Document) {
    val root = document.documentElement ?: error("Gboard Settings XML has no root element.")
    if (root.hasThemeStudioPreference()) return

    val category = document.createElement("androidx.preference.PreferenceCategory").apply {
        setAttributeNS(THEME_STUDIO_ANDROID_NS, "android:title", "Keyboard Themes")
    }
    category.appendChild(
        document.createElement("Preference").apply {
            setAttributeNS(THEME_STUDIO_ANDROID_NS, "android:persistent", "false")
            setAttributeNS(THEME_STUDIO_ANDROID_NS, "android:title", "Open Theme Studio")
            setAttributeNS(
                THEME_STUDIO_ANDROID_NS,
                "android:summary",
                "Create, edit, import, and export Gboard themes",
            )
            setAttributeNS(THEME_STUDIO_ANDROID_NS, "android:key", "pref_key_gboard_theme_studio")
        }
    )
    root.appendChild(category)
}

private fun Element.hasThemeStudioPreference(): Boolean {
    if (getAttributeNS(THEME_STUDIO_ANDROID_NS, "key") == "pref_key_gboard_theme_studio") return true
    val children = childNodes
    for (index in 0 until children.length) {
        val child = children.item(index) as? Element ?: continue
        if (child.hasThemeStudioPreference()) return true
    }
    return false
}

private fun addThemeStudioActivity(document: Document) {
    val application = document.getElementsByTagName("application").item(0) as? Element
        ?: error("AndroidManifest.xml application element not found.")
    val activities = application.getElementsByTagName("activity")
    for (index in 0 until activities.length) {
        val activity = activities.item(index) as? Element ?: continue
        if (activity.getAttributeNS(THEME_STUDIO_ANDROID_NS, "name") == THEME_STUDIO_ACTIVITY) return
    }
    application.appendChild(
        document.createElement("activity").apply {
            setAttributeNS(THEME_STUDIO_ANDROID_NS, "android:name", THEME_STUDIO_ACTIVITY)
            setAttributeNS(THEME_STUDIO_ANDROID_NS, "android:exported", "false")
            setAttributeNS(
                THEME_STUDIO_ANDROID_NS,
                "android:theme",
                "@android:style/Theme.Material.Light.NoActionBar",
            )
        }
    )
}
