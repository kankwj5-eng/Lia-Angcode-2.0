package com.kankwj.angcode.runtime

class ArchiveInstallBudget(
    private val maxArchiveBytes: Long = MAX_ARCHIVE_BYTES,
    private val maxExtractedBytes: Long = MAX_EXTRACTED_BYTES,
    private val maxEntries: Int = MAX_ENTRIES,
    private val maxSymlinks: Int = MAX_SYMLINKS
) {
    var archiveBytes: Long = 0
        private set
    var extractedBytes: Long = 0
        private set
    var entries: Int = 0
        private set
    var symlinks: Int = 0
        private set

    fun onArchiveBytes(delta: Long) {
        require(delta >= 0) { "delta de archivo inválido" }
        archiveBytes = Math.addExact(archiveBytes, delta)
        require(archiveBytes <= maxArchiveBytes) {
            "Bootstrap rechazado: ZIP excede el límite permitido"
        }
    }

    fun onExtractedBytes(delta: Long) {
        require(delta >= 0) { "delta extraído inválido" }
        extractedBytes = Math.addExact(extractedBytes, delta)
        require(extractedBytes <= maxExtractedBytes) {
            "Bootstrap rechazado: contenido descomprimido excede el límite permitido"
        }
    }

    fun onEntry() {
        entries = Math.addExact(entries, 1)
        require(entries <= maxEntries) {
            "Bootstrap rechazado: demasiadas entradas ZIP"
        }
    }

    fun onSymlink() {
        symlinks = Math.addExact(symlinks, 1)
        require(symlinks <= maxSymlinks) {
            "Bootstrap rechazado: demasiados symlinks"
        }
    }

    companion object {
        const val MAX_ARCHIVE_BYTES: Long = 2_500L * 1024L * 1024L
        const val MAX_EXTRACTED_BYTES: Long = 8L * 1024L * 1024L * 1024L
        const val MAX_ENTRIES: Int = 250_000
        const val MAX_SYMLINKS: Int = 100_000
        const val MAX_SYMLINK_TABLE_BYTES: Int = 8 * 1024 * 1024
    }
}
