package app.freeyourself.dns

import org.junit.Assert.assertEquals
import org.junit.Test

class DnsFilterTest {
    @Test fun withoutNetworkStatusIsUnknown() {
        assertEquals(DnsStatus.Offline, dnsStatus(online = false, serverName = "adult-filter-dns.cleanbrowsing.org"))
    }

    @Test fun noStrictPrivateDnsMeansOff() {
        assertEquals(DnsStatus.Off, dnsStatus(online = true, serverName = null))
        assertEquals(DnsStatus.Off, dnsStatus(online = true, serverName = " "))
    }

    @Test fun recognizesKnownFilters() {
        assertEquals(DnsStatus.Filtering(DnsProvider.CLEANBROWSING), dnsStatus(true, "adult-filter-dns.cleanbrowsing.org"))
        assertEquals(DnsStatus.Filtering(DnsProvider.CLEANBROWSING), dnsStatus(true, "Adult-Filter-DNS.CleanBrowsing.org "))
        assertEquals(DnsStatus.Filtering(DnsProvider.CLOUDFLARE), dnsStatus(true, "family.cloudflare-dns.com"))
    }

    @Test fun otherPrivateDnsIsReportedAsIs() {
        assertEquals(DnsStatus.Other("dns.google"), dnsStatus(true, "dns.google"))
    }

    @Test fun labels() {
        assertEquals("Sem internet para verificar", DnsStatus.Offline.label())
        assertEquals("Desligado", DnsStatus.Off.label())
        assertEquals("Ativo (CleanBrowsing Adulto)", DnsStatus.Filtering(DnsProvider.CLEANBROWSING).label())
        assertEquals("Outro DNS privado: dns.google", DnsStatus.Other("dns.google").label())
    }
}
