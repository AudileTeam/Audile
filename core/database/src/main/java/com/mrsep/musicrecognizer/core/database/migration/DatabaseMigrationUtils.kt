package com.mrsep.musicrecognizer.core.database.migration

import android.util.Log
import androidx.sqlite.SQLiteConnection

internal object DatabaseMigrationUtils {

    internal fun SQLiteConnection.isSQLiteVersionAtLeast(version: String): Boolean? {
        val thisVersion = querySQLiteVersion() ?: return null
        return try {
            compareSQLiteVersions(thisVersion, version) != -1
        } catch (e: Exception) {
            Log.e(this::class.simpleName, "Failed to parse SQLiteVersion", e)
            null
        }
    }

    private fun SQLiteConnection.querySQLiteVersion(): String? {
        return prepare("SELECT sqlite_version()").use { statement ->
            if (!statement.step()) return null
            if (statement.isNull(0)) null else statement.getText(0)
        }
    }

    private fun compareSQLiteVersions(version1: String, version2: String): Int {
        val pattern = Regex("^\\d+(\\.\\d+)*\$")
        require(pattern.matches(version1)) {
            "Incorrect version format:$version1"
        }
        require(pattern.matches(version2)) {
            "Incorrect version format:$version2"
        }
        val partsThis = version1.split('.')
        val partsOther = version2.split('.')
        val maxLength = maxOf(partsThis.size, partsOther.size)
        for (i in 0 until maxLength) {
            val partThis = partsThis.getOrNull(i)?.toIntOrNull() ?: 0
            val partOther = partsOther.getOrNull(i)?.toIntOrNull() ?: 0

            when {
                partThis > partOther -> return 1
                partThis < partOther -> return -1
            }
        }
        return 0
    }
}
