package com.blacksingularities.opencall.data

/**
 * vCard 3.0: zapis kontaktów do udostępniania i kopii zapasowej, odczyt wersji 2.1/3.0/4.0
 * (QUOTED-PRINTABLE, zawijane linie, CHARSET). Bez Androida – testowane jednostkowo.
 */
object VCard {
    fun write(d: ContactDraft): String = buildString {
        fun line(s: String) {
            // Linie dłuższe niż 75 znaków są zawijane (RFC 6350 §3.2).
            var rest = s
            var first = true
            while (rest.length > 75) {
                append(if (first) "" else " ").append(rest.take(75)).append("\r\n")
                rest = rest.drop(75)
                first = false
            }
            append(if (first) "" else " ").append(rest).append("\r\n")
        }
        line("BEGIN:VCARD")
        line("VERSION:3.0")
        line("N:" + listOf(d.family, d.given, d.middle, d.prefix, d.suffix).joinToString(";") { esc(it) })
        line("FN:" + esc(d.displayName.ifBlank { d.organization.ifBlank { d.phones.firstOrNull()?.value ?: "" } }))
        if (d.phoneticGiven.isNotBlank()) line("X-PHONETIC-FIRST-NAME:" + esc(d.phoneticGiven))
        if (d.phoneticMiddle.isNotBlank()) line("X-PHONETIC-MIDDLE-NAME:" + esc(d.phoneticMiddle))
        if (d.phoneticFamily.isNotBlank()) line("X-PHONETIC-LAST-NAME:" + esc(d.phoneticFamily))
        if (d.nickname.isNotBlank()) line("NICKNAME:" + esc(d.nickname))
        if (d.organization.isNotBlank() || d.department.isNotBlank()) {
            line("ORG:" + esc(d.organization) + if (d.department.isNotBlank()) ";" + esc(d.department) else "")
        }
        if (d.title.isNotBlank()) line("TITLE:" + esc(d.title))
        if (d.jobDescription.isNotBlank()) line("ROLE:" + esc(d.jobDescription))
        d.phones.filter { it.value.isNotBlank() }.forEach { line("TEL;TYPE=${phoneType(it.type)}${pref(it)}:" + it.value.trim()) }
        d.emails.filter { it.value.isNotBlank() }.forEach { line("EMAIL;TYPE=${emailType(it.type)}${pref(it)}:" + esc(it.value.trim())) }
        d.addresses.filter { !it.isBlank }.forEach { a ->
            val type = when (a.type) {
                1 -> "HOME"
                2 -> "WORK"
                else -> "OTHER"
            }
            line("ADR;TYPE=$type:;;" + listOf(a.street, a.city, a.region, a.postcode, a.country).joinToString(";") { esc(it.trim()) })
        }
        d.websites.filter { it.value.isNotBlank() }.forEach { w ->
            val type = when (w.type) {
                2 -> ";TYPE=BLOG"
                3 -> ";TYPE=PROFILE"
                4 -> ";TYPE=HOME"
                5 -> ";TYPE=WORK"
                else -> ""
            }
            line("URL$type:" + esc(w.value.trim()))
        }
        if (d.birthday.isNotBlank()) line("BDAY:" + d.birthday.trim())
        d.events.filter { it.value.isNotBlank() }.forEach { e ->
            // Rocznica ma pole w vCard 4.0; w 3.0 zapisuje ją Apple i Google jako X-ANNIVERSARY.
            if (e.type == 1) line("X-ANNIVERSARY:" + e.value.trim()) else line("X-EVENT;X-ANDROID-TYPE=${e.type}${label(e)}:" + e.value.trim())
        }
        d.relations.filter { it.value.isNotBlank() }.forEach { r ->
            line("RELATED;TYPE=${relatedType(r.type)};X-ANDROID-TYPE=${r.type}${label(r)}:" + esc(r.value.trim()))
        }
        d.ims.filter { it.value.isNotBlank() }.forEach { im ->
            val service = ContactKinds.imName(im.type, im.label).ifBlank { "IM" }
            val scheme = service.lowercase().filter { it.isLetterOrDigit() }.ifEmpty { "im" }.let { if (it == "xmpp") "xmpp" else it }
            line("IMPP;X-SERVICE-TYPE=" + escParam(service) + ":" + scheme + ":" + esc(im.value.trim()))
        }
        d.sip.filter { it.value.isNotBlank() }.forEach { line("IMPP;X-SERVICE-TYPE=SIP:sip:" + esc(it.value.trim().removePrefix("sip:"))) }
        if (d.note.isNotBlank()) line("NOTE:" + esc(d.note))
        line("END:VCARD")
    }

    private fun pref(f: DraftField) = if (f.primary) ",PREF" else ""

    /** Własna etykieta pola (rodzaj CUSTOM) jako parametr X-LABEL. */
    private fun label(f: DraftField) = if (f.type == ContactKinds.CUSTOM && !f.label.isNullOrBlank()) ";X-LABEL=" + escParam(f.label) else ""

    private fun escParam(s: String) = "\"" + s.replace("\"", "'") + "\""

    // Relation.TYPE_* → typy RELATED z RFC 6350.
    private fun relatedType(t: Int) = when (t) {
        14 -> "spouse"
        10, 4 -> "sweetheart"
        3 -> "child"
        9, 5, 8 -> "parent"
        2, 13 -> "sibling"
        12 -> "kin"
        6 -> "friend"
        1 -> "agent"
        7 -> "co-worker"
        else -> "contact"
    }

    /** Rodzaj Androida zapisany przez OpenDial w parametrze X-ANDROID-TYPE. */
    private fun androidType(types: List<String>): Int? =
        types.firstOrNull { it.startsWith("X-ANDROID-TYPE=") }?.substringAfter('=')?.toIntOrNull()

    private fun xLabel(rawParams: List<String>): String? =
        rawParams.firstOrNull { it.uppercase().startsWith("X-LABEL=") }?.substringAfter('=')?.trim('"')?.takeIf { it.isNotBlank() }

    private fun relationTypeOf(types: List<String>): Int = when {
        "SPOUSE" in types -> 14
        "SWEETHEART" in types -> 10
        "CHILD" in types -> 3
        "PARENT" in types -> 9
        "SIBLING" in types -> 13
        "KIN" in types -> 12
        "FRIEND" in types -> 6
        "AGENT" in types -> 1
        "CO-WORKER" in types -> 7
        else -> 0
    }

    fun writeAll(list: List<ContactDraft>): String = list.joinToString("") { write(it) }

    fun parse(text: String): List<ContactDraft> {
        val out = ArrayList<ContactDraft>()
        var cur: ContactDraft? = null
        var fn = ""
        for (raw in unfold(text)) {
            val colon = findColon(raw)
            if (colon < 0) continue
            val head = raw.substring(0, colon)
            var value = raw.substring(colon + 1)
            val parts = head.split(';')
            val name = parts[0].substringAfter('.').uppercase()
            val params = parts.drop(1).map { it.uppercase() }
            if (params.any { it == "ENCODING=QUOTED-PRINTABLE" || it == "QUOTED-PRINTABLE" }) {
                val charset = params.firstOrNull { it.startsWith("CHARSET=") }?.substringAfter('=') ?: "UTF-8"
                value = decodeQp(value, charset)
            }
            val types = params.flatMap { p -> p.removePrefix("TYPE=").split(',') }
            val rawParams = parts.drop(1)
            when (name) {
                "BEGIN" -> if (value.equals("VCARD", true)) {
                    cur = ContactDraft(phones = emptyList())
                    fn = ""
                }
                "END" -> if (value.equals("VCARD", true)) {
                    cur?.let { c ->
                        val withName = if (c.displayName.isBlank() && fn.isNotBlank()) c.copy(given = fn) else c
                        if (!withName.isEmpty) out.add(withName)
                    }
                    cur = null
                }
                "N" -> cur = cur?.let { c ->
                    val n = splitEsc(value, ';')
                    c.copy(
                        family = n.getOrElse(0) { "" },
                        given = n.getOrElse(1) { "" },
                        middle = n.getOrElse(2) { "" },
                        prefix = n.getOrElse(3) { "" },
                        suffix = n.getOrElse(4) { "" },
                    )
                }
                "FN" -> fn = unesc(value)
                "X-PHONETIC-FIRST-NAME" -> cur = cur?.copy(phoneticGiven = unesc(value))
                "X-PHONETIC-MIDDLE-NAME" -> cur = cur?.copy(phoneticMiddle = unesc(value))
                "X-PHONETIC-LAST-NAME" -> cur = cur?.copy(phoneticFamily = unesc(value))
                "NICKNAME" -> cur = cur?.copy(nickname = unesc(value))
                "ORG" -> cur = cur?.let { c ->
                    val o = splitEsc(value, ';')
                    c.copy(organization = o.getOrElse(0) { "" }.trim(), department = o.drop(1).filter { it.isNotBlank() }.joinToString(", "))
                }
                "TITLE" -> cur = cur?.copy(title = unesc(value))
                "ROLE" -> cur = cur?.copy(jobDescription = unesc(value))
                "TEL" -> cur = cur?.let { c -> c.copy(phones = c.phones + DraftField(value.removePrefix("tel:").trim(), phoneTypeOf(types), primary = "PREF" in types)) }
                "EMAIL" -> cur = cur?.let { c -> c.copy(emails = c.emails + DraftField(unesc(value).trim(), emailTypeOf(types), primary = "PREF" in types)) }
                "ADR" -> cur = cur?.let { c ->
                    val a = splitEsc(value, ';')
                    val street = listOf(a.getOrElse(0) { "" }, a.getOrElse(1) { "" }, a.getOrElse(2) { "" }).map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
                    val p = PostalField(
                        street = street,
                        city = a.getOrElse(3) { "" }.trim(),
                        region = a.getOrElse(4) { "" }.trim(),
                        postcode = a.getOrElse(5) { "" }.trim(),
                        country = a.getOrElse(6) { "" }.trim(),
                        type = when {
                            "WORK" in types -> 2
                            "OTHER" in types -> 3
                            else -> 1
                        },
                    )
                    if (p.isBlank) c else c.copy(addresses = c.addresses + p)
                }
                "URL" -> cur = cur?.let { c ->
                    val type = when {
                        "BLOG" in types -> 2
                        "PROFILE" in types -> 3
                        "HOME" in types -> 4
                        "WORK" in types -> 5
                        else -> 1
                    }
                    c.copy(websites = c.websites + DraftField(unesc(value).trim(), type))
                }
                "BDAY" -> cur = cur?.copy(birthday = value.trim())
                "ANNIVERSARY", "X-ANNIVERSARY" -> cur = cur?.let { c -> c.copy(events = c.events + DraftField(value.trim(), 1)) }
                "X-EVENT" -> cur = cur?.let { c ->
                    val type = androidType(types) ?: 2
                    c.copy(events = c.events + DraftField(value.trim(), type, xLabel(rawParams)))
                }
                "RELATED" -> cur = cur?.let { c ->
                    val type = androidType(types) ?: relationTypeOf(types)
                    // RELATED bywa adresem (urn:uuid:…, mailto:) – zapisujemy tylko nazwy osób.
                    val v = unesc(value).trim()
                    if (v.isEmpty() || v.startsWith("urn:", true)) c else c.copy(relations = c.relations + DraftField(v, type, xLabel(rawParams)))
                }
                "X-SPOUSE", "X-ASSISTANT", "X-MANAGER" -> cur = cur?.let { c ->
                    val type = when (name) {
                        "X-SPOUSE" -> 14
                        "X-ASSISTANT" -> 1
                        else -> 7
                    }
                    c.copy(relations = c.relations + DraftField(unesc(value).trim(), type))
                }
                "IMPP" -> cur = cur?.let { c ->
                    val v = unesc(value).trim()
                    val scheme = v.substringBefore(':', "").lowercase()
                    val handle = if (scheme.isNotEmpty()) v.substringAfter(':') else v
                    if (scheme == "sip") {
                        c.copy(sip = c.sip + DraftField(handle, 3))
                    } else {
                        val service = rawParams.firstOrNull { it.uppercase().startsWith("X-SERVICE-TYPE=") }?.substringAfter('=')?.trim('"') ?: scheme
                        val (protocol, custom) = ContactKinds.imOf(service.ifBlank { "IM" })
                        c.copy(ims = c.ims + DraftField(handle, protocol, custom))
                    }
                }
                "X-SIP" -> cur = cur?.let { c -> c.copy(sip = c.sip + DraftField(unesc(value).trim().removePrefix("sip:"), 3)) }
                "X-SKYPE", "X-SKYPE-USERNAME", "X-JABBER", "X-ICQ", "X-QQ", "X-AIM", "X-MSN", "X-YAHOO", "X-GOOGLE-TALK", "X-TELEGRAM", "X-SIGNAL", "X-WHATSAPP" ->
                    cur = cur?.let { c ->
                        val (protocol, custom) = ContactKinds.imOf(name.removePrefix("X-").removeSuffix("-USERNAME").replace("GOOGLE-TALK", "gtalk"))
                        c.copy(ims = c.ims + DraftField(unesc(value).trim(), protocol, custom))
                    }
                "NOTE" -> cur = cur?.copy(note = unesc(value))
            }
        }
        return out
    }

    // Typy numerów jak w ContactsContract.CommonDataKinds.Phone.
    private fun phoneType(t: Int) = when (t) {
        1 -> "HOME"
        2 -> "CELL"
        3 -> "WORK"
        4 -> "WORK,FAX"
        5 -> "HOME,FAX"
        6 -> "PAGER"
        else -> "VOICE"
    }

    private fun phoneTypeOf(types: List<String>) = when {
        "FAX" in types && "WORK" in types -> 4
        "FAX" in types -> 5
        "CELL" in types || "MOBILE" in types -> 2
        "WORK" in types -> 3
        "HOME" in types -> 1
        "PAGER" in types -> 6
        else -> 7
    }

    private fun emailType(t: Int) = when (t) {
        1 -> "HOME"
        2 -> "WORK"
        else -> "INTERNET"
    }

    private fun emailTypeOf(types: List<String>) = when {
        "WORK" in types -> 2
        "HOME" in types -> 1
        else -> 3
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;")

    private fun unesc(s: String) = s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private fun splitEsc(s: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                cur.append(c).append(s[i + 1])
                i += 2
                continue
            }
            if (c == sep) {
                out.add(unesc(cur.toString()))
                cur.clear()
            } else {
                cur.append(c)
            }
            i++
        }
        out.add(unesc(cur.toString()))
        return out
    }

    /** Dwukropek oddzielający nazwę od wartości – poza cudzysłowem parametrów. */
    private fun findColon(s: String): Int {
        var quoted = false
        s.forEachIndexed { i, c ->
            if (c == '"') quoted = !quoted
            if (c == ':' && !quoted) return i
        }
        return -1
    }

    /** Łączy linie zawinięte (spacja/tab na początku) i miękkie podziały QUOTED-PRINTABLE („=” na końcu). */
    private fun unfold(text: String): List<String> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = ArrayList<String>()
        for (l in lines) {
            if ((l.startsWith(" ") || l.startsWith("\t")) && out.isNotEmpty()) {
                out[out.size - 1] = out.last() + l.substring(1)
            } else if (out.isNotEmpty() && out.last().endsWith("=") && out.last().uppercase().contains("QUOTED-PRINTABLE")) {
                out[out.size - 1] = out.last().dropLast(1) + l
            } else if (l.isNotBlank()) {
                out.add(l)
            }
        }
        return out
    }

    private fun decodeQp(s: String, charset: String): String {
        val bytes = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '=' && i + 3 <= s.length) {
                val v = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (v != null) {
                    bytes.write(v)
                    i += 3
                    continue
                }
            }
            bytes.write(c.code and 0xFF)
            i++
        }
        return runCatching { String(bytes.toByteArray(), charset(charset)) }.getOrElse { String(bytes.toByteArray()) }
    }
}
