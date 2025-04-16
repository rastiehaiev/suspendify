package io.github.rastiehaiev

import java.io.File
import java.nio.file.Files

fun log(message: String) {
    File("/Users/roman/dev/project/personal/coroutine-friendly").resolve("output.txt")
        .also { if (!it.exists()) Files.createFile(it.toPath()) }
        .appendText("$message\n----------------------------\n")
}
