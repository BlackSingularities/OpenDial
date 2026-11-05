package com.blacksingularities.opencall.launcher

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.net.Uri
import android.provider.ContactsContract
import android.view.View
import android.widget.RemoteViews
import com.blacksingularities.opencall.MainActivity
import com.blacksingularities.opencall.R
import com.blacksingularities.opencall.data.Contact
import com.blacksingularities.opencall.i18n.tr
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Skróty z ikony aplikacji (przytrzymanie ikony) i widżet ulubionych na ekranie głównym.
 * Oba dzwonią przez MainActivity intencją z tajnym tokenem – inna aplikacja nie może
 * w ten sposób zadzwonić bez zgody użytkownika, bo tokenu nie zna.
 */
object Launcher {
    const val ACTION_CALL_FAVORITE = "com.blacksingularities.opencall.CALL_FAVORITE"
    const val EXTRA_TOKEN = "token"
    private const val PREFS = "launcher"
    private const val MAX_WIDGET = 4

    data class Fav(val name: String, val number: String)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Losowy token tej instalacji – sprawdzany przy każdym połączeniu ze skrótu lub widżetu. */
    fun token(context: Context): String {
        val p = prefs(context)
        return p.getString("token", null) ?: UUID.randomUUID().toString().also { p.edit().putString("token", it).apply() }
    }

    fun isTrusted(context: Context, intent: Intent): Boolean =
        intent.getStringExtra(EXTRA_TOKEN)?.let { it == token(context) } == true

    fun callIntent(context: Context, number: String): Intent =
        Intent(ACTION_CALL_FAVORITE, Uri.fromParts("tel", number, null))
            .setClass(context, MainActivity::class.java)
            .putExtra(EXTRA_TOKEN, token(context))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** Aktualizuje skróty i widżet po wczytaniu kontaktów; nic nie robi, gdy ulubieni się nie zmienili. */
    fun update(context: Context, contacts: List<Contact>) {
        val favs = contacts.asSequence()
            .filter { it.starred }
            .mapNotNull { c -> c.primaryNumber?.let { Fav(c.name, it.number) } }
            .take(MAX_WIDGET)
            .toList()
        val json = JSONArray(favs.map { JSONObject().put("name", it.name).put("number", it.number) }).toString()
        val p = prefs(context)
        if (p.getString("favs", null) == json) return
        p.edit().putString("favs", json).apply()
        runCatching { updateShortcuts(context, favs) }
        runCatching { FavoritesWidget.refresh(context) }
    }

    fun favorites(context: Context): List<Fav> = runCatching {
        val a = JSONArray(prefs(context).getString("favs", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Fav(o.getString("name"), o.getString("number")) } }
    }.getOrDefault(emptyList())

    private fun updateShortcuts(context: Context, favs: List<Fav>) {
        val sm = context.getSystemService(ShortcutManager::class.java) ?: return
        val max = sm.maxShortcutCountPerActivity.coerceAtMost(4)
        val list = ArrayList<ShortcutInfo>()
        list += ShortcutInfo.Builder(context, "new_contact")
            .setShortLabel(tr("Nowy kontakt"))
            .setIcon(Icon.createWithAdaptiveBitmap(letterBitmap("+")))
            .setIntent(Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).setClass(context, MainActivity::class.java))
            .setRank(favs.size)
            .build()
        favs.take(max - 1).forEachIndexed { i, f ->
            list += ShortcutInfo.Builder(context, "fav_$i")
                .setShortLabel(f.name)
                .setLongLabel(tr("Zadzwoń do {0}", f.name))
                .setIcon(Icon.createWithAdaptiveBitmap(letterBitmap(f.name.take(1).uppercase())))
                .setIntent(callIntent(context, f.number))
                .setRank(i)
                .build()
        }
        sm.dynamicShortcuts = list
    }

    /** Ikona skrótu: litera na ciemnym tle (bitmapa adaptacyjna – kształt przycina launcher). */
    private fun letterBitmap(text: String): Bitmap {
        val size = 216
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF1C1C1C.toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = size * 0.36f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val y = size / 2f - (p.descent() + p.ascent()) / 2f
        c.drawText(text.ifBlank { "?" }, size / 2f, y, p)
        return bmp
    }
}

/** Widżet „Ulubione”: do czterech osób, dotknięcie dzwoni. */
class FavoritesWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val v = views(context)
        ids.forEach { manager.updateAppWidget(it, v) }
    }

    companion object {
        private val slots = intArrayOf(R.id.fav0, R.id.fav1, R.id.fav2, R.id.fav3)

        fun refresh(context: Context) {
            val m = AppWidgetManager.getInstance(context) ?: return
            val ids = m.getAppWidgetIds(ComponentName(context, FavoritesWidget::class.java))
            if (ids.isEmpty()) return
            val v = views(context)
            ids.forEach { m.updateAppWidget(it, v) }
        }

        private fun views(context: Context): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_favorites)
            val favs = Launcher.favorites(context)
            v.setTextViewText(R.id.widget_title, tr("Ulubione"))
            v.setTextViewText(R.id.widget_empty, tr("Oznacz kontakty gwiazdką, aby pojawiły się tutaj"))
            v.setViewVisibility(R.id.widget_empty, if (favs.isEmpty()) View.VISIBLE else View.GONE)
            v.setOnClickPendingIntent(
                R.id.widget_title,
                PendingIntent.getActivity(context, 100, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
            slots.forEachIndexed { i, id ->
                val f = favs.getOrNull(i)
                if (f == null) {
                    v.setViewVisibility(id, View.GONE)
                } else {
                    v.setViewVisibility(id, View.VISIBLE)
                    v.setTextViewText(id, f.name)
                    v.setContentDescription(id, tr("Zadzwoń do {0}", f.name))
                    v.setOnClickPendingIntent(
                        id,
                        PendingIntent.getActivity(
                            context, i, Launcher.callIntent(context, f.number),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        ),
                    )
                }
            }
            return v
        }
    }
}
