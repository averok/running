package app.running.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    private val hex = "a".repeat(64)

    private fun release(
        tag: String = "v0.3.0",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: String = """[{"name":"running-0.3.0.apk","browser_download_url":"https://github.com/x/running/releases/download/v0.3.0/running-0.3.0.apk","size":1234,"digest":"sha256:$hex"}]""",
    ) = """{"tag_name":"$tag","body":" Notas ","draft":$draft,"prerelease":$prerelease,"assets":$assets,"otro":"ignorado"}"""

    @Test fun parseVersion_acepta_formas_validas() {
        assertEquals(listOf(0, 3, 0), UpdateCheck.parseVersion("v0.3.0"))
        assertEquals(listOf(1, 12, 4), UpdateCheck.parseVersion("1.12.4"))
        assertEquals(listOf(2, 0, 0), UpdateCheck.parseVersion("2.0"))
    }

    @Test fun parseVersion_rechaza_basura() {
        for (s in listOf("", "v", "latest", "1", "1.2.3.4", "1.x.3", "v1.2.3-beta", "-1.0.0")) {
            assertNull("'$s' no debería ser una versión", UpdateCheck.parseVersion(s))
        }
    }

    @Test fun isNewer_compara_numericamente_no_como_texto() {
        assertTrue(UpdateCheck.isNewer("0.10.0", "0.9.0")) // como texto "0.10" < "0.9"
        assertTrue(UpdateCheck.isNewer("v1.0.0", "0.99.99"))
        assertTrue(UpdateCheck.isNewer("0.2.1", "0.2.0"))
        assertFalse(UpdateCheck.isNewer("0.2.0", "0.2.0"))
        assertFalse(UpdateCheck.isNewer("0.1.9", "0.2.0"))
        assertTrue(UpdateCheck.isNewer("0.3", "0.2.9"))
    }

    @Test fun isNewer_con_versiones_ilegibles_es_false() {
        assertFalse(UpdateCheck.isNewer("nightly", "0.2.0"))
        assertFalse(UpdateCheck.isNewer("0.3.0", "debug"))
    }

    @Test fun parseRelease_normal() {
        val r = UpdateCheck.parseRelease(release())
        assertNotNull(r)
        r!!
        assertEquals("0.3.0", r.version) // sin la "v"
        assertEquals("Notas", r.notes)
        assertEquals("running-0.3.0.apk", r.apkName)
        assertEquals(1234L, r.apkSize)
        assertEquals(hex, r.sha256)
    }

    @Test fun parseRelease_descarta_borradores_y_prereleases() {
        assertNull(UpdateCheck.parseRelease(release(draft = true)))
        assertNull(UpdateCheck.parseRelease(release(prerelease = true)))
    }

    @Test fun parseRelease_sin_apk_o_con_tag_raro_es_null() {
        assertNull(UpdateCheck.parseRelease(release(assets = "[]")))
        assertNull(UpdateCheck.parseRelease(release(assets = """[{"name":"notas.txt","browser_download_url":"https://x/notas.txt"}]""")))
        assertNull(UpdateCheck.parseRelease(release(tag = "nightly")))
    }

    @Test fun parseRelease_json_invalido_es_null() {
        assertNull(UpdateCheck.parseRelease("no es json"))
        assertNull(UpdateCheck.parseRelease("""{"sin":"tag"}"""))
        assertNull(UpdateCheck.parseRelease(""))
    }

    @Test fun parseRelease_rechaza_http_salvo_que_se_permita() {
        val http = release(assets = """[{"name":"a.apk","browser_download_url":"http://10.0.2.2:8000/a.apk"}]""")
        assertNull(UpdateCheck.parseRelease(http))
        assertNotNull(UpdateCheck.parseRelease(http, allowInsecureUrls = true))
        val ftp = release(assets = """[{"name":"a.apk","browser_download_url":"ftp://x/a.apk"}]""")
        assertNull(UpdateCheck.parseRelease(ftp, allowInsecureUrls = true))
    }

    @Test fun parseRelease_ignora_digest_mal_formado() {
        for (d in listOf("sha1:$hex", "sha256:zz", "sha256:${"a".repeat(63)}", "")) {
            val r = UpdateCheck.parseRelease(release(assets = """[{"name":"a.apk","browser_download_url":"https://x/a.apk","digest":"$d"}]"""))
            assertNotNull(r)
            assertNull("digest '$d' no debería aceptarse", r!!.sha256)
        }
    }

    @Test fun parseRelease_elige_el_primer_apk() {
        val assets = """[{"name":"notas.txt","browser_download_url":"https://x/n.txt"},{"name":"b.APK","browser_download_url":"https://x/b.APK"},{"name":"c.apk","browser_download_url":"https://x/c.apk"}]"""
        assertEquals("b.APK", UpdateCheck.parseRelease(release(assets = assets))!!.apkName)
    }
}
