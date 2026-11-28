package com.example.utils

import android.content.res.AssetFileDescriptor
import android.content.res.ColorStateList
import android.content.res.Resources
import android.content.res.TypedArray
import android.content.res.XmlResourceParser
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import java.io.InputStream

/**
 * Prevents Android's native C++ AssetManager2::FindEntry from logging
 * "E/com.socialindia: No package ID xx found for resource ID 0x........"
 * when libraries (such as Navigation Compose's NavDestination.getDisplayName
 * or Coil's ResourceIntMapper) probe synthetic/hashed integer IDs (like route hash 0x78d52eb5)
 * that do not belong to the app package (0x7f) or Android framework package (0x01).
 */
@Suppress("DEPRECATION")
class SafeResources(val baseResources: Resources) : Resources(
    baseResources.assets,
    baseResources.displayMetrics,
    baseResources.configuration
) {
    init {
        syncResourcesImpl()
    }

    private fun syncResourcesImpl() {
        try {
            val field = Resources::class.java.getDeclaredField("mResourcesImpl")
            field.isAccessible = true
            val impl = field.get(baseResources)
            if (impl != null) {
                field.set(this, impl)
            }
        } catch (_: Throwable) {
        }
    }

    private fun isValidPackage(id: Int): Boolean {
        val pkgId = id ushr 24
        return pkgId == 0x7f || pkgId == 0x01
    }

    private fun ensureValidPackage(id: Int) {
        if (!isValidPackage(id)) {
            throw NotFoundException("Resource ID #0x${Integer.toHexString(id)}")
        }
    }

    override fun getResourceName(resid: Int): String {
        ensureValidPackage(resid)
        return baseResources.getResourceName(resid)
    }

    override fun getResourcePackageName(resid: Int): String {
        ensureValidPackage(resid)
        return baseResources.getResourcePackageName(resid)
    }

    override fun getResourceTypeName(resid: Int): String {
        ensureValidPackage(resid)
        return baseResources.getResourceTypeName(resid)
    }

    override fun getResourceEntryName(resid: Int): String {
        ensureValidPackage(resid)
        return baseResources.getResourceEntryName(resid)
    }

    override fun getValue(id: Int, outValue: TypedValue?, resolveRefs: Boolean) {
        ensureValidPackage(id)
        baseResources.getValue(id, outValue, resolveRefs)
    }

    override fun getValueForDensity(
        id: Int,
        density: Int,
        outValue: TypedValue?,
        resolveRefs: Boolean
    ) {
        ensureValidPackage(id)
        baseResources.getValueForDensity(id, density, outValue, resolveRefs)
    }

    override fun getText(id: Int): CharSequence {
        ensureValidPackage(id)
        return baseResources.getText(id)
    }

    override fun getText(id: Int, def: CharSequence?): CharSequence? {
        if (!isValidPackage(id)) return def
        return baseResources.getText(id, def)
    }

    override fun getString(id: Int): String {
        ensureValidPackage(id)
        return baseResources.getString(id)
    }

    override fun getString(id: Int, vararg formatArgs: Any?): String {
        ensureValidPackage(id)
        return baseResources.getString(id, *formatArgs)
    }

    override fun getDrawable(id: Int): Drawable {
        ensureValidPackage(id)
        return baseResources.getDrawable(id)
    }

    override fun getDrawable(id: Int, theme: Theme?): Drawable {
        ensureValidPackage(id)
        return baseResources.getDrawable(id, theme)
    }

    override fun getDrawableForDensity(id: Int, density: Int): Drawable? {
        ensureValidPackage(id)
        return baseResources.getDrawableForDensity(id, density)
    }

    override fun getDrawableForDensity(id: Int, density: Int, theme: Theme?): Drawable? {
        ensureValidPackage(id)
        return baseResources.getDrawableForDensity(id, density, theme)
    }

    override fun getColor(id: Int): Int {
        ensureValidPackage(id)
        return baseResources.getColor(id)
    }

    override fun getColor(id: Int, theme: Theme?): Int {
        ensureValidPackage(id)
        return baseResources.getColor(id, theme)
    }

    override fun getColorStateList(id: Int): ColorStateList {
        ensureValidPackage(id)
        return baseResources.getColorStateList(id)
    }

    override fun getColorStateList(id: Int, theme: Theme?): ColorStateList {
        ensureValidPackage(id)
        return baseResources.getColorStateList(id, theme)
    }

    override fun getBoolean(id: Int): Boolean {
        ensureValidPackage(id)
        return baseResources.getBoolean(id)
    }

    override fun getInteger(id: Int): Int {
        ensureValidPackage(id)
        return baseResources.getInteger(id)
    }

    override fun getLayout(id: Int): XmlResourceParser {
        ensureValidPackage(id)
        return baseResources.getLayout(id)
    }

    override fun getAnimation(id: Int): XmlResourceParser {
        ensureValidPackage(id)
        return baseResources.getAnimation(id)
    }

    override fun getXml(id: Int): XmlResourceParser {
        ensureValidPackage(id)
        return baseResources.getXml(id)
    }

    override fun openRawResource(id: Int): InputStream {
        ensureValidPackage(id)
        return baseResources.openRawResource(id)
    }

    override fun openRawResource(id: Int, value: TypedValue?): InputStream {
        ensureValidPackage(id)
        return baseResources.openRawResource(id, value)
    }

    override fun openRawResourceFd(id: Int): AssetFileDescriptor {
        ensureValidPackage(id)
        return baseResources.openRawResourceFd(id)
    }

    override fun obtainTypedArray(id: Int): TypedArray {
        ensureValidPackage(id)
        return baseResources.obtainTypedArray(id)
    }

    override fun getFont(id: Int): Typeface {
        ensureValidPackage(id)
        return baseResources.getFont(id)
    }
}
