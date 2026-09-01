package com.eottadwotji.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** 주차 사진 파일 관리 — 앱 내부 저장소(photos/)에 보관, FileProvider로 카메라에 전달 */
object PhotoStore {

    private const val AUTHORITY = "com.eottadwotji.fileprovider"

    /** 카메라 촬영 결과를 받을 새 파일 URI 생성 */
    fun newPhotoUri(context: Context, timestampMs: Long): Uri {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, "parking_$timestampMs.jpg")
        return FileProvider.getUriForFile(context, AUTHORITY, file)
    }

    /** 저장된 사진 로드 (없거나 손상 시 null) */
    fun loadPhoto(context: Context, uriString: String): Bitmap? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(uriString))?.use {
            BitmapFactory.decodeStream(it)
        }
    }.getOrNull()
}
