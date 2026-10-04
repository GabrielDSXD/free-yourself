package app.freeyourself.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceGuideTest {
    @Test fun detectsBrands() {
        assertEquals(Brand.SAMSUNG, detectBrand("samsung", "samsung"))
        assertEquals(Brand.MOTOROLA, detectBrand("motorola", "motorola"))
        assertEquals(Brand.XIAOMI, detectBrand("Xiaomi", "Redmi"))
        assertEquals(Brand.XIAOMI, detectBrand("Xiaomi", "POCO"))
        assertEquals(Brand.XIAOMI, detectBrand("Xiaomi", "xiaomi"))
        assertEquals(Brand.OTHER, detectBrand("Google", "google"))
        assertEquals(Brand.OTHER, detectBrand("", ""))
    }

    @Test fun onlyXiaomiNeedsAutostart() {
        assertNotNull(guideFor(Brand.XIAOMI).autostart)
        listOf(Brand.SAMSUNG, Brand.MOTOROLA, Brand.OTHER).forEach { assertNull(guideFor(it).autostart) }
    }

    @Test fun stepsFollowEachBrandsMenus() {
        assertTrue(guideFor(Brand.SAMSUNG).dns.contains("Mais configurações de conexão"))
        assertTrue(guideFor(Brand.SAMSUNG).accessibility.contains("Aplicativos instalados"))
        assertTrue(guideFor(Brand.XIAOMI).dns.contains("Conexão e compartilhamento"))
        assertTrue(guideFor(Brand.XIAOMI).battery.contains("Economia de bateria"))
        assertTrue(guideFor(Brand.MOTOROLA).accessibility.contains("Apps baixados"))
    }

    @Test fun everyBrandHasEveryStep() {
        Brand.entries.map(::guideFor).forEach { g ->
            listOf(g.name, g.accessibility, g.restricted, g.battery, g.dns).forEach { assertTrue(it.isNotBlank()) }
        }
    }
}
