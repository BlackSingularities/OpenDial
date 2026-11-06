package com.blacksingularities.opencall

import com.blacksingularities.opencall.data.ContactDraft
import com.blacksingularities.opencall.data.ContactKinds
import com.blacksingularities.opencall.data.DraftField
import com.blacksingularities.opencall.data.PostalField
import com.blacksingularities.opencall.data.VCard
import com.blacksingularities.opencall.update.isNewer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardTest {
    @Test
    fun roundTrip() {
        val d = ContactDraft(
            given = "Łucja",
            family = "Wiśniewska; Nowak",
            organization = "Firma, sp. z o.o.",
            phones = listOf(DraftField("+48 600 100 200", 2), DraftField("22 123 45 67", 3)),
            emails = listOf(DraftField("lucja@example.com", 1)),
            birthday = "1990-05-12",
            note = "Pierwsza linia\nDruga linia",
        )
        val parsed = VCard.parse(VCard.write(d)).single()
        assertEquals("Łucja", parsed.given)
        assertEquals("Wiśniewska; Nowak", parsed.family)
        assertEquals("Firma, sp. z o.o.", parsed.organization)
        assertEquals(listOf("+48 600 100 200", "22 123 45 67"), parsed.phones.map { it.value })
        assertEquals(2, parsed.phones[0].type)
        assertEquals(3, parsed.phones[1].type)
        assertEquals("lucja@example.com", parsed.emails.single().value)
        assertEquals("1990-05-12", parsed.birthday)
        assertEquals("Pierwsza linia\nDruga linia", parsed.note)
    }

    @Test
    fun roundTripExtendedFields() {
        val d = ContactDraft(
            given = "Jan",
            family = "Kowalski",
            phoneticGiven = "Jan",
            phoneticFamily = "Kowalski",
            organization = "ACME",
            department = "Dział IT",
            title = "Kierownik",
            jobDescription = "Utrzymanie serwerów",
            phones = listOf(DraftField("600100200", 2), DraftField("221234567", 3, primary = true)),
            addresses = listOf(PostalField(street = "ul. Długa 1", postcode = "00-001", city = "Warszawa", country = "Polska", type = 2)),
            websites = listOf(DraftField("https://blog.example.com", 2)),
            events = listOf(DraftField("2015-06-20", 1), DraftField("--03-08", 0, "Imieniny")),
            relations = listOf(DraftField("Anna Kowalska", 14)),
            ims = listOf(DraftField("+48600100200", ContactKinds.IM_CUSTOM, "Signal"), DraftField("jan.k", 3)),
            sip = listOf(DraftField("jan@sip.example.com", 3)),
        )
        val p = VCard.parse(VCard.write(d)).single()
        assertEquals("Kowalski", p.phoneticFamily)
        assertEquals("ACME", p.organization)
        assertEquals("Dział IT", p.department)
        assertEquals("Utrzymanie serwerów", p.jobDescription)
        assertTrue(p.phones[1].primary)
        assertFalse(p.phones[0].primary)
        val a = p.addresses.single()
        assertEquals("ul. Długa 1", a.street)
        assertEquals("00-001", a.postcode)
        assertEquals("Warszawa", a.city)
        assertEquals("Polska", a.country)
        assertEquals(2, a.type)
        assertEquals(2, p.websites.single().type)
        assertEquals(listOf("2015-06-20", "--03-08"), p.events.map { it.value })
        assertEquals(1, p.events[0].type)
        assertEquals("Imieniny", p.events[1].label)
        assertEquals("Anna Kowalska", p.relations.single().value)
        assertEquals(14, p.relations.single().type)
        assertEquals(listOf("Signal", null), p.ims.map { it.label })
        assertEquals(listOf("+48600100200", "jan.k"), p.ims.map { it.value })
        assertEquals(3, p.ims[1].type)
        assertEquals("jan@sip.example.com", p.sip.single().value)
    }

    @Test
    fun eventDates() {
        assertEquals(Triple(1990, 5, 12), ContactKinds.parseDate("1990-05-12"))
        assertEquals(Triple(null, 3, 8), ContactKinds.parseDate("--03-08"))
        assertEquals(Triple(null, 3, 8), ContactKinds.parseDate("--0308"))
        assertEquals(null, ContactKinds.parseDate("12.05.1990"))
        assertEquals("--03-08", ContactKinds.formatDate(null, 3, 8))
        assertEquals("1990-05-12", ContactKinds.formatDate(1990, 5, 12))
    }

    @Test
    fun fieldValidation() {
        assertTrue(ContactKinds.validEmail("jan@example.com"))
        assertTrue(ContactKinds.validEmail(""))
        assertFalse(ContactKinds.validEmail("jan@example"))
        assertTrue(ContactKinds.validPhone("+48 600-100-200"))
        assertTrue(ContactKinds.validPhone("*100#"))
        assertFalse(ContactKinds.validPhone("12"))
        assertFalse(ContactKinds.validPhone("abc123456"))
    }

    @Test
    fun parsesVersion21QuotedPrintable() {
        val text = """
            BEGIN:VCARD
            VERSION:2.1
            N;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:Kowalski;=C5=81ukasz;;;
            TEL;CELL:600100200
            TEL;HOME;VOICE:221234567
            END:VCARD
            BEGIN:VCARD
            VERSION:3.0
            FN:Tylko Nazwa
            TEL;TYPE=WORK:+48 500
            END:VCARD
        """.trimIndent()
        val list = VCard.parse(text)
        assertEquals(2, list.size)
        assertEquals("Łukasz", list[0].given)
        assertEquals("Kowalski", list[0].family)
        assertEquals(2, list[0].phones[0].type)
        assertEquals(1, list[0].phones[1].type)
        assertEquals("Tylko Nazwa", list[1].displayName)
    }

    @Test
    fun foldedLinesAreJoined() {
        val text = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Jan\r\nNOTE:abc\r\n def\r\nEND:VCARD\r\n"
        assertEquals("abcdef", VCard.parse(text).single().note)
    }

    @Test
    fun skipsEmptyCards() {
        assertTrue(VCard.parse("BEGIN:VCARD\nVERSION:3.0\nEND:VCARD").isEmpty())
    }

    @Test
    fun versionCompare() {
        assertTrue(isNewer("0.1.10", "0.1.9"))
        assertTrue(isNewer("0.2.0", "0.1.0-dev"))
        assertTrue(isNewer("0.1.0.57", "0.1.0"))
        assertFalse(isNewer("0.1.0", "0.1.0"))
        assertFalse(isNewer("0.0.9", "0.1.0"))
    }
}
