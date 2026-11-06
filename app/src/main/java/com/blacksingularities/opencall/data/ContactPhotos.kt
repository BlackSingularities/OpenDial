@file:Suppress("DEPRECATION") // Im.PROTOCOL_* – nadal jedyny sposób zapisu komunikatorów w ContactsContract.

package com.blacksingularities.opencall.data

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Im
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.SipAddress
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import com.blacksingularities.opencall.i18n.tr
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/** Zdjęcia kontaktu: wczytanie do kadrowania (z obrotem EXIF) i zapis wyciętego kwadratu. */
internal class ContactPhotos(private val repo: ContactsRepo) {
    private val cr get() = repo.resolver

    /** Wczytuje zdjęcie do kadrowania: zmniejszone do ~2048 px i obrócone zgodnie z EXIF. */
    fun loadPhotoForCrop(uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 2048 || bounds.outHeight / (sample * 2) >= 2048) sample *= 2
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val rotation = runCatching {
            cr.openInputStream(uri)?.use { when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
        if (rotation == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
    }.getOrNull()

    /** Wycina kwadrat (współrzędne w pikselach [bmp]) i zapisuje jako JPEG do 720×720. */
    fun cropPhoto(bmp: Bitmap, left: Int, top: Int, side: Int): ByteArray? = runCatching {
        val s = side.coerceIn(1, minOf(bmp.width, bmp.height))
        val x = left.coerceIn(0, bmp.width - s)
        val y = top.coerceIn(0, bmp.height - s)
        val square = Bitmap.createBitmap(bmp, x, y, s, s)
        val scaled = if (s > 720) Bitmap.createScaledBitmap(square, 720, 720, true) else square
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
    }.getOrNull()
}
