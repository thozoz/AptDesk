package com.aptdesk.app

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

class RootfsManager(private val context: Context) {
    private val rootfsDir = File(context.filesDir, "rootfs")
    private val readyMarker = File(rootfsDir, ".aptdesk-rootfs-ready")

    @Throws(IOException::class, IllegalStateException::class)
    fun ensureRootfs() {
        if (readyMarker.exists()) {
            return
        }
        val rootfsUrl = BuildConfig.ROOTFS_URL
        require(rootfsUrl.isNotBlank()) { "ROOTFS_URL is not configured" }

        if (rootfsDir.exists()) {
            deleteRecursively(rootfsDir)
        }
        if (!rootfsDir.mkdirs() && !rootfsDir.exists()) {
            throw IOException("Unable to create rootfs directory: ${rootfsDir.path}")
        }

        val archiveFile = File(context.cacheDir, "aptdesk-rootfs.tar.gz")
        downloadRootfs(rootfsUrl, archiveFile)
        extractArchive(archiveFile, rootfsDir)
        readyMarker.writeText("ready")
    }

    private fun downloadRootfs(url: String, destination: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true

        if (connection.responseCode !in 200..299) {
            throw IOException("Rootfs download failed: HTTP ${connection.responseCode}")
        }

        connection.inputStream.use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun extractArchive(archive: File, targetDir: File) {
        FileInputStream(archive).use { fileInput ->
            GZIPInputStream(fileInput).use { gzipInput ->
                TarArchiveInputStream(gzipInput).use { tarInput ->
                    var entry: TarArchiveEntry? = tarInput.nextTarEntry
                    while (entry != null) {
                        handleEntry(entry, tarInput, targetDir)
                        entry = tarInput.nextTarEntry
                    }
                }
            }
        }
    }

    private fun handleEntry(
        entry: TarArchiveEntry,
        tarInput: TarArchiveInputStream,
        targetDir: File
    ) {
        val outputFile = resolveEntry(targetDir, entry.name)
        if (entry.isDirectory) {
            if (!outputFile.exists() && !outputFile.mkdirs()) {
                throw IOException("Failed to create directory: ${outputFile.path}")
            }
            return
        }

        if (entry.isSymbolicLink) {
            createSymlink(outputFile, entry.linkName)
            return
        }

        if (entry.isLink) {
            val linkTarget = resolveEntry(targetDir, entry.linkName)
            if (!linkTarget.exists()) {
                throw IOException("Missing hard link target: ${entry.linkName}")
            }
            copyFile(linkTarget, outputFile)
            return
        }

        outputFile.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw IOException("Failed to create parent directory: ${parent.path}")
            }
        }

        FileOutputStream(outputFile).use { output ->
            tarInput.copyTo(output)
        }
        applyExecutableFlag(entry, outputFile)
    }

    private fun resolveEntry(targetDir: File, entryName: String): File {
        val targetCanonical = targetDir.canonicalFile
        val outputFile = File(targetDir, entryName)
        val outputCanonical = outputFile.canonicalFile
        val targetPath = targetCanonical.path + File.separator
        if (!outputCanonical.path.startsWith(targetPath)) {
            throw IOException("Blocked path traversal entry: $entryName")
        }
        return outputFile
    }

    private fun createSymlink(linkFile: File, target: String) {
        linkFile.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw IOException("Failed to create symlink parent: ${parent.path}")
            }
        }
        if (linkFile.exists()) {
            linkFile.delete()
        }
        try {
            Os.symlink(target, linkFile.absolutePath)
        } catch (error: ErrnoException) {
            throw IOException("Symlink failed for ${linkFile.path}", error)
        }
    }

    private fun applyExecutableFlag(entry: TarArchiveEntry, outputFile: File) {
        val mode = entry.mode
        val isExecutable = mode and 0b001 != 0 || mode and 0b010 != 0 || mode and 0b100 != 0
        if (isExecutable && !outputFile.setExecutable(true, false)) {
            Log.w(TAG, "Unable to set executable bit: ${outputFile.path}")
        }
    }

    private fun copyFile(source: File, destination: File) {
        destination.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw IOException("Failed to create parent directory: ${parent.path}")
            }
        }
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun deleteRecursively(target: File) {
        if (target.isDirectory) {
            target.listFiles()?.forEach { child ->
                deleteRecursively(child)
            }
        }
        if (!target.delete()) {
            throw IOException("Failed to delete ${target.path}")
        }
    }

    companion object {
        private const val TAG = "RootfsManager"
    }
}
