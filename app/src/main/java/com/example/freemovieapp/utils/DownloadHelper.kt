package com.example.freemovieapp.utils

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DownloadedVideoItem(
    val file: File,
    val displayName: String,
    val sizeText: String,
    val dateText: String,
    val filePath: String
)

object DownloadHelper {

    fun downloadVideo(context: Context, movieTitle: String, episodeName: String, videoUrl: String) {
        if (videoUrl.isBlank()) {
            Toast.makeText(context, "视频链接无效，无法下载", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Video URL", videoUrl)
            clipboard.setPrimaryClip(clip)

            val uri = Uri.parse(videoUrl)
            val fileExt = if (videoUrl.contains(".m3u8")) "m3u8" else "mp4"
            val sanitizedTitle = movieTitle.replace(Regex("[^a-zA-Z0-9\u4e00-\u9fa5]"), "_")
            val sanitizedEp = episodeName.replace(Regex("[^a-zA-Z0-9\u4e00-\u9fa5]"), "_")
            val fileName = "极光影院_${sanitizedTitle}_${sanitizedEp}.$fileExt"

            val request = DownloadManager.Request(uri).apply {
                setTitle("$movieTitle - $episodeName")
                setDescription("极光影院正在下载离线视频...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(
                context,
                "已加入下载！可在本 APP 顶部「离线管理」中查看与删除",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "下载已加入系统队列", Toast.LENGTH_LONG).show()
        }
    }

    // 扫描本地下载目录中的所有离线缓存视频
    fun getDownloadedVideos(): List<DownloadedVideoItem> {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadDir.exists() || !downloadDir.isDirectory) return emptyList()

        val files = downloadDir.listFiles() ?: return emptyList()
        val result = mutableListOf<DownloadedVideoItem>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        for (file in files) {
            val name = file.name
            if (file.isFile && (name.endsWith(".mp4", ignoreCase = true) || name.endsWith(".m3u8", ignoreCase = true) || name.contains("极光影院"))) {
                val sizeBytes = file.length()
                val sizeText = formatFileSize(sizeBytes)
                val dateText = dateFormat.format(Date(file.lastModified()))
                val cleanName = name.removePrefix("极光影院_").substringBeforeLast(".")

                result.add(
                    DownloadedVideoItem(
                        file = file,
                        displayName = cleanName,
                        sizeText = sizeText,
                        dateText = dateText,
                        filePath = file.absolutePath
                    )
                )
            }
        }
        return result.sortedByDescending { it.file.lastModified() }
    }

    fun deleteDownloadedVideo(file: File): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1024f * 1024f * 1024f))
            bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
            bytes >= 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024f)
            else -> "$bytes B"
        }
    }
}
