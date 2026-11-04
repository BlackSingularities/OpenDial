package com.blacksingularities.opencall

import com.blacksingularities.opencall.update.isNewer
import com.blacksingularities.opencall.update.parseRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    private val release = """
        {
          "tag_name": "v0.2.0",
          "name": "OpenDial 0.2.0",
          "html_url": "https://github.com/BlackSingularities/OpenDial/releases/tag/v0.2.0",
          "body": "Nowości\n- blokowanie prefiksów\n\n---\nStopka wydania, której nie pokazujemy",
          "assets": [
            {"name": "mapping.txt", "browser_download_url": "https://example.com/mapping.txt", "size": 10},
            {"name": "OpenDial-0.2.0.apk", "browser_download_url": "https://example.com/app.apk", "size": 7340032}
          ]
        }
    """.trimIndent()

    @Test
    fun parsesTagAndAsset() {
        val info = parseRelease(release)
        assertEquals("0.2.0", info.version)
        assertEquals("https://example.com/app.apk", info.assetUrl)
        assertEquals(7_340_032L, info.sizeBytes)
        assertEquals("", info.pageUrl)
    }

    @Test
    fun skipsWatchApkAndPicksPhoneOne() {
        val json = """
            {
              "tag_name": "v0.3.0",
              "assets": [
                {"name": "OpenDial-Watch-0.3.0.apk", "browser_download_url": "https://example.com/watch.apk", "size": 1},
                {"name": "OpenDial-0.3.0.apk", "browser_download_url": "https://example.com/phone.apk", "size": 2}
              ]
            }
        """.trimIndent()
        assertEquals("https://example.com/phone.apk", parseRelease(json).assetUrl)
    }

    @Test
    fun releaseWithoutApkFails() {
        val json = """{"tag_name":"v0.2.0","assets":[{"name":"notes.txt","browser_download_url":"u","size":1}]}"""
        assertThrows(IllegalStateException::class.java) { parseRelease(json) }
    }

    @Test
    fun releaseWithoutAssetsFails() {
        assertThrows(IllegalStateException::class.java) { parseRelease("""{"tag_name":"v0.2.0"}""") }
    }

    @Test
    fun comparesVersionsNumericallyNotAlphabetically() {
        assertTrue(isNewer("0.1.12", "0.1.9"))
        assertTrue(isNewer("0.2.0", "0.1.99"))
        assertTrue(isNewer("1.0.0", "0.9.9"))
        assertFalse(isNewer("0.1.0", "0.1.0"))
        assertFalse(isNewer("0.1.0", "0.1.1"))
        assertFalse(isNewer("0.0.9", "0.1.0"))
    }

    @Test
    fun ignoresSuffixesAndMissingParts() {
        assertFalse(isNewer("0.1.0", "0.1.0-dev"))
        assertTrue(isNewer("0.1.1", "0.1.0-dev"))
        assertFalse(isNewer("0.1", "0.1.0"))
        assertTrue(isNewer("0.1.1", "0.1"))
    }
}
