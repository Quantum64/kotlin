/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.tasks

import org.gradle.api.file.*
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Provider
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.*

internal class TaskOutputsBackup(
    val fileSystemOperations: FileSystemOperations,
    val buildDirectory: DirectoryProperty,
    val snapshotsDir: Provider<Directory>,
    val outputs: FileCollection,
    val logger: Logger
) {
    fun createSnapshot() {
        // Kotlin JS compilation task declares one file from 'destinationDirectory' output as task `@OutputFile'
        // property. To avoid snapshot sync collisions, each snapshot output directory has also 'index' as prefix.
        outputs.files.toSortedSet().forEachIndexed { index, outputPath ->
            val pathInSnapshot = "$index${File.separator}${outputPath.pathRelativeToBuildDirectory}"
            if (outputPath.isDirectory) {
                snapshotsDir
                    .map { it.file(pathInSnapshot) }
                    .get()
                    .asFile
                    .run {
                        compressDirectoryToZip(
                            File(this, DIRECTORY_SNAPSHOT_ARCHIVE_FILE),
                            outputPath
                        )
                    }
            } else {
                fileSystemOperations.copy { spec ->
                    spec.from(outputPath)
                    spec.into(snapshotsDir.map { it.file(pathInSnapshot).asFile.parentFile })
                }
            }
        }
    }

    fun restoreOutputs() {
        fileSystemOperations.delete {
            it.delete(outputs)
        }

        outputs.files.toSortedSet().forEachIndexed { index, outputPath ->
            val pathInSnapshot = "$index${File.separator}${outputPath.pathRelativeToBuildDirectory}"
            val fileInSnapshot = snapshotsDir.get().file(pathInSnapshot).asFile
            if (fileInSnapshot.isDirectory) {
                val snapshotArchive = File(fileInSnapshot, DIRECTORY_SNAPSHOT_ARCHIVE_FILE)
                if (!snapshotArchive.exists()) {
                    logger.warn(
                        """
                        |Failed to restore task outputs as snapshot file ${snapshotArchive.absolutePath} does not exist!
                        |On recompilation full rebuild will be performed.
                        """.trimMargin()
                    )
                    return
                }
                uncompressZipIntoDirectory(snapshotArchive, outputPath)
            } else {
                fileSystemOperations.copy { spec ->
                    spec.from(snapshotsDir.map { it.file(pathInSnapshot).asFile.parentFile })
                    spec.into(outputPath.parentFile)
                }
            }
        }
    }

    fun deleteSnapshot() {
        fileSystemOperations.delete { it.delete(snapshotsDir) }
    }

    /**
     * Kotlin's compilation in a "fat" project may contain a lot of small files that is slow to copy
     * So we speeding it up by archiving them into single zip file without compression. Such approach reduces snapshotting
     * time up to half ot the time needed to copy similar files.
     */
    private fun compressDirectoryToZip(
        snapshotFile: File,
        outputPath: File
    ) {
        snapshotFile.parentFile.mkdirs()
        snapshotFile.createNewFile()

        ZipOutputStream(snapshotFile.outputStream().buffered()).use { zip ->
            zip.setLevel(Deflater.NO_COMPRESSION)
            outputPath
                .walkTopDown()
                .filter { !it.isDirectory }
                .forEach { file ->
                    val entry = ZipEntry(file.relativeTo(outputPath).toString())
                    zip.putNextEntry(entry)
                    file.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            zip.flush()
            zip.close()
        }
    }

    private fun uncompressZipIntoDirectory(
        snapshotFile: File,
        outputDirectory: File
    ) {
        val outputPath = outputDirectory.toPath()
        val snapshotUri = URI.create("jar:file:${snapshotFile.absolutePath}")
        FileSystems.newFileSystem(snapshotUri, emptyMap<String, Any>()).use { zipFs ->
            zipFs.rootDirectories.forEach { rootDir ->
                Files.walk(rootDir).use { paths ->
                    paths.forEach {
                        if (Files.isDirectory(it)) {
                            Files.createDirectories(outputPath.resolve(it.normalizedToBeRelative))
                        } else if (Files.isRegularFile(it)) {
                            Files.copy(it, outputPath.resolve(it.normalizedToBeRelative), StandardCopyOption.REPLACE_EXISTING)
                        }
                    }
                }
            }
        }
    }

    private val Path.normalizedToBeRelative: String
        get() = if (toString() == "/") "." else toString().removePrefix("/")

    private val File.pathRelativeToBuildDirectory: String
        get() {
            val buildDir = buildDirectory.get().asFile
            return relativeTo(buildDir).path
        }

    companion object {
        private const val DIRECTORY_SNAPSHOT_ARCHIVE_FILE = "snapshot.zip"
    }
}
