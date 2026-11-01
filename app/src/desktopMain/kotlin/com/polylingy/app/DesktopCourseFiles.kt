package com.polylingy.app

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Native file dialogs via AWT; blocking is fine since they are modal anyway. */
class DesktopCourseFiles(private val owner: Frame) : CourseFiles {
    override fun open(): PickedFile? {
        val dialog = FileDialog(owner, "Import course", FileDialog.LOAD).apply {
            file = "*.json;*.zip"
            setFilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) || name.endsWith(".zip", ignoreCase = true) }
            isVisible = true
        }
        val name = dialog.file ?: return null
        return PickedFile(name, File(dialog.directory, name).readBytes())
    }

    override fun save(suggestedName: String, bytes: ByteArray): Boolean {
        val dialog = FileDialog(owner, "Export course", FileDialog.SAVE).apply {
            file = suggestedName
            isVisible = true
        }
        val name = dialog.file ?: return false
        File(dialog.directory, name).writeBytes(bytes)
        return true
    }
}
