package com.solvesall.ktor_template.core.extend

import java.io.File
import java.io.FileOutputStream


public fun String.ext_resourceFile(): File {
    val inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(this)!!

    val tempFile = File.createTempFile(inputStream.hashCode().toString(), ".tmp")
    tempFile.deleteOnExit()

    FileOutputStream(tempFile).use { out ->
        val buffer = ByteArray(1024)
        var bytesRead: Int
        while ((inputStream.read(buffer).also { bytesRead = it }) != -1) {
            out.write(buffer, 0, bytesRead)
        }
    }
    return tempFile
}
