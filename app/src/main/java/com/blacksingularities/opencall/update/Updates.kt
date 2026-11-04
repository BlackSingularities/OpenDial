package com.blacksingularities.opencall.update

import com.blacksingularities.opencall.i18n.tr
import org.json.JSONObject
import java.io.File

/** Repozytorium z wydaniami aplikacji. */
const val UPDATE_REPO = "BlackSingularities/OpenDial"
const val UPDATE_API = "https://redzikowski.com/api/github/repos/$UPDATE_REPO/releases/latest"

data class ReleaseInfo(
    val version: String,
    val assetUrl: String,
    val sizeBytes: Long,
    val pageUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val progress: Float) : UpdateState
    data class Ready(val info: ReleaseInfo, val file: File) : UpdateState
    data class Installing(val info: ReleaseInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Najnowsze wydanie z odpowiedzi GitHub API; [assetSuffix] – końcówka pliku (.apk). */
fun parseRelease(json: String, assetSuffix: String = ".apk"): ReleaseInfo {
    val o = JSONObject(json)
    var url = ""
    var size = 0L
    val assets = o.optJSONArray("assets")
    if (assets != null) {
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name")
            // Wydanie zawiera też plik aplikacji na zegarek – telefon pobiera tylko swój.
            if (name.contains("watch", ignoreCase = true)) continue
            if (name.endsWith(assetSuffix)) {
                url = a.optString("browser_download_url")
                size = a.optLong("size")
                break
            }
        }
    }
    if (url.isEmpty()) error(tr("Wydanie nie zawiera pliku {0}", assetSuffix.removePrefix(".").uppercase()))
    return ReleaseInfo(
        version = o.optString("tag_name").removePrefix("v"),
        assetUrl = url,
        sizeBytes = size,
        pageUrl = "",
    )
}

/** Porównanie numeryczne wersji „0.1.12” > „0.1.9”; przyrostki typu „-dev” są pomijane. */
fun isNewer(remote: String, local: String): Boolean {
    fun parts(v: String) = v.split('.', '-').mapNotNull { it.toIntOrNull() }
    val r = parts(remote)
    val l = parts(local)
    for (i in 0 until maxOf(r.size, l.size)) {
        val a = r.getOrElse(i) { 0 }
        val b = l.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}
