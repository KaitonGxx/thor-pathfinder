package com.thorpathfinder.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * qs_page1.xml and qs_page2.xml are `uiautomator dump`s of an AYN Thor's
 * expanded Quick Settings, one per page. The Screen record tile is on page 2.
 */
class ScreenRecordTest {

    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private val page1 = ScreenRecord.parse(resource("qs_page1.xml"))
    private val page2 = ScreenRecord.parse(resource("qs_page2.xml"))

    @Test
    fun readsTheTilesOnEachPageInOrder() {
        assertEquals(
            listOf("Internet", "Fan", "Bluetooth", "Joystick LED", "Controller style", "Smooth Display",
                "Charging separation", "Charging Limit"),
            page1.tiles.map { it.first },
        )
        assertEquals(
            listOf("Auto-rotate", "Airplane mode", "JamesDSP", "Force landscape", "ClusterTune", "Screen Cast",
                "Screen record"),
            page2.tiles.map { it.first },
        )
    }

    @Test
    fun knowsThePagerAndWhichPageIsShown() {
        assertEquals(Box(37, 309, 1883, 748), page1.pager)
        assertEquals(1, page1.page)
        assertEquals(2, page1.pages)
        assertEquals(2, page2.page)
        assertEquals(2, page2.pages)
    }

    @Test
    fun findsTheTileWhereverItIsAndIgnoresCase() {
        assertNull(page1.tile("Screen record"))
        val box = page2.tile("screen RECORD")
        assertEquals(Box(1089, 612, 1313, 655), box)
        assertEquals(1201, box!!.centerX)
        assertEquals(633, box.centerY)
        // "Screen Cast" must never pass for it
        assertEquals(Box(616, 612, 840, 655), page2.tile("Screen Cast"))
    }

    @Test
    fun aClosedPanelParsesAsEmpty() {
        val closed = ScreenRecord.parse("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><hierarchy rotation=\"1\" />")
        assertTrue(closed.tiles.isEmpty())
        assertNull(closed.pager)
        assertEquals(1, closed.page)
        assertEquals(1, closed.pages)
    }

    @Test
    fun decodesXmlEntitiesInLabels() {
        val panel = ScreenRecord.parse(
            """<node index="0" text="Wi-Fi &amp; data" resource-id="com.android.systemui:id/tile_label" class="android.widget.TextView" bounds="[10,10][110,40]" />"""
        )
        assertEquals(listOf("Wi-Fi & data"), panel.tiles.map { it.first })
    }

    @Test
    fun visitsTheShownPageThenLaterOnesThenEarlierOnes() {
        assertEquals(listOf(1, 2), ScreenRecord.visitOrder(1, 2))
        assertEquals(listOf(2, 1), ScreenRecord.visitOrder(2, 2))
        assertEquals(listOf(2, 3, 4, 1), ScreenRecord.visitOrder(2, 4))
        assertEquals(listOf(1), ScreenRecord.visitOrder(1, 1))
    }

    @Test
    fun rememberedBoundsSurviveARoundTrip() {
        val box = Box(37, 309, 1883, 748)
        assertEquals(box, Box.decode(box.encode()))
        assertNull(Box.decode(null))
        assertNull(Box.decode("37,309"))
        assertNull(Box.decode("a,b,c,d"))
    }

    @Test
    fun onlyProblemsGetAMessage() {
        assertNull(screenRecordOutcomeMessage(English, ScreenRecord.Outcome.Opened))
        assertEquals(
            "Add the Screen record tile to Quick Settings",
            screenRecordOutcomeMessage(English, ScreenRecord.Outcome.TileNotFound),
        )
    }
}
