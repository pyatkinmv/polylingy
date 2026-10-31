package com.polylingy.app

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Native file dialogs via AWT; blocking is fine since they are modal anyway. */
class DesktopCourseFiles(private val owner: Frame) : CourseFiles {
    override fun open(): String? {
        val dialog = FileDialog(owner, "Import course", FileDialog.LOAD).apply {
            file = "*.json"
            isVisible = true
        }
        val name = dialog.file ?: return null
        return File(dialog.directory, name).readText()
    }

    override fun save(suggestedName: String, content: String): Boolean {
        val dialog = FileDialog(owner, "Export course", FileDialog.SAVE).apply {
            file = suggestedName
            isVisible = true
        }
        val name = dialog.file ?: return false
        File(dialog.directory, name).writeText(content)
        return true
    }
}
