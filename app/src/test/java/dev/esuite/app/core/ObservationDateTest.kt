package dev.esuite.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

class ObservationDateTest {
    @Test fun formatterUsesTheCurrentTimeZoneWhenRecreated() {
        val original = TimeZone.getDefault()
        try {
            repeat(25) { minute ->
                val moment = Instant.parse("2025-01-01T00:00:00Z").plusSeconds(minute * 60L)
                val formattedMinute = minute.toString().padStart(2, '0')
                TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
                assertEquals("2025-01-01 00:$formattedMinute:00 UTC", observationDateFormatter(Locale.US).format(moment))
                TimeZone.setDefault(TimeZone.getTimeZone("GMT+09:00"))
                assertEquals("2025-01-01 09:$formattedMinute:00 GMT+09:00", observationDateFormatter(Locale.US).format(moment))
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test fun formatterUsesTheRequestedLocale() {
        val moment = Instant.parse("2025-01-01T00:00:00Z")
        val zone = ZoneId.of("America/Los_Angeles")
        val us = observationDateFormatter(Locale.US, zone).format(moment)
        val arabic = observationDateFormatter(Locale.forLanguageTag("ar-EG"), zone).format(moment)
        assertEquals("2024-12-31 16:00:00", us.substringBeforeLast(' '))
        assertEquals("٢٠٢٤-١٢-٣١ ١٦:٠٠:٠٠", arabic.substringBeforeLast(' '))
        assertNotEquals(us, arabic)
    }
}
