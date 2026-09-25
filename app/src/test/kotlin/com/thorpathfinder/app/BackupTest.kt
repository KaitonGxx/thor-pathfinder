package com.thorpathfinder.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private val values: Map<String, Any> = mapOf(
        "map.BACK.HOLD" to "SWAP_SCREENS",
        "map.AYN.DOUBLE" to "PROFILE",
        "map.AYN.DOUBLE.profile" to "ENABLE",
        "map.AYN.DOUBLE.profileId" to 2,
        "combo.BACK+A" to "SCREENSHOT",
        "combo.BACK+L1+R1" to "NOTIFICATIONS",
        "combo.BACK+X" to "NORMAL",
        "holdMs" to 900L,
        "vibrate" to false,
        "scale" to 1.5f,
        "keepRunning" to setOf("com.spotify.music", "de.langerhans.odintools"),
    )

    @Test
    fun everyKindOfSettingComesBackAsItWas() {
        val text = Backup.encode(values).toString()
        assertEquals(values, Backup.decode(JSONObject(text)))
    }

    @Test
    fun anythingUnreadableIsLeftOut() {
        val json = JSONObject()
            .put("good", JSONArray().put("s").put("x"))
            .put("unknownType", JSONArray().put("q").put(1))
            .put("noValue", JSONArray().put("s"))
            .put("notTagged", "plain")
        assertEquals(mapOf<String, Any>("good" to "x"), Backup.decode(json))
    }

    @Test
    fun aSharedProfileKeepsPointingAtItself() {
        val shared = Backup.retarget(values, from = 2, to = 5)
        assertEquals(5, shared["map.AYN.DOUBLE.profileId"])
        assertEquals("ENABLE", shared["map.AYN.DOUBLE.profile"])
    }

    @Test
    fun aSharedProfilePointingElsewhereCyclesInstead() {
        val shared = Backup.retarget(values, from = 7, to = 5)
        assertFalse(shared.containsKey("map.AYN.DOUBLE.profileId"))
        assertEquals(ProfileSwitch.CYCLE.name, shared["map.AYN.DOUBLE.profile"])
        assertEquals("SWAP_SCREENS", shared["map.BACK.HOLD"])
    }

    @Test
    fun aNameAlreadyTakenGetsANumber() {
        assertEquals("Games", Backup.uniqueName(listOf("General", "racing"), "Games"))
        assertEquals("racing (2)", Backup.uniqueName(listOf("General", "Racing"), "racing"))
        assertEquals("racing (3)", Backup.uniqueName(listOf("racing", "racing (2)"), "racing"))
        val long = "A very long name here"
        val named = Backup.uniqueName(listOf(long.take(Profiles.MAX_NAME)), long.take(Profiles.MAX_NAME))
        assertTrue(named.length <= Profiles.MAX_NAME)
        assertTrue(named.endsWith(" (2)"))
    }

    @Test
    fun countsLeaveOutNormalAndExtras() {
        assertEquals(2 to 2, Backup.counts(values))
    }

    @Test
    fun aFileFromElsewhereIsRefused() {
        assertTrue(Backup.open("not json") is Backup.Opened.Unreadable)
        assertTrue(Backup.open("""{"app":"Something else","format":1}""") is Backup.Opened.Unreadable)
        assertTrue(Backup.open("""{"app":"Thor Pathfinder","format":1,"kind":"mystery"}""") is Backup.Opened.Unreadable)
    }

    @Test
    fun aNewerFormatIsRefusedAndSaysWhy() {
        val opened = Backup.open("""{"app":"Thor Pathfinder","format":${Backup.FORMAT + 1},"version":"2.0.0"}""")
        assertTrue(opened is Backup.Opened.Unreadable)
        assertEquals("2.0.0", (opened as Backup.Opened.Unreadable).detail)
    }

    @Test
    fun aDamagedBackupIsRefused() {
        // Lists profile 2, which isn't in the file.
        val list = Backup.encode(mapOf("ids" to "0,2"))
        val json = header("backup").put("list", list).put("profiles", JSONObject().put("0", Backup.encode(emptyMap<String, Any>())))
        assertTrue(Backup.open(json.toString()) is Backup.Opened.Unreadable)
    }

    @Test
    fun aBackupSaysWhatItHolds() {
        val list = Backup.encode(
            mapOf(
                "ids" to "0,2",
                "name.0" to "General",
                "name.2" to "racing",
                "apps.2" to setOf("com.discord"),
            ),
        )
        val profiles = JSONObject().put("0", Backup.encode(values)).put("2", Backup.encode(mapOf("combo.HOME+A" to "BACK")))
        val opened = Backup.open(header("backup").put("list", list).put("profiles", profiles).toString())
        opened as Backup.Opened.Whole
        assertEquals(listOf("General", "racing"), opened.profiles)
        assertEquals(3, opened.combos)
        assertEquals(1, opened.apps)
        assertEquals("2026-09-25", opened.date)
        assertEquals("1.0.0", opened.version)
    }

    @Test
    fun aSharedProfileSaysWhatItHolds() {
        val json = header("profile")
            .put("id", 2)
            .put("name", "racing")
            .put("shortcuts", Backup.encode(values))
            .put("apps", JSONArray().put("com.discord"))
        val opened = Backup.open(json.toString())
        opened as Backup.Opened.Shared
        assertEquals("racing", opened.name)
        assertEquals(2, opened.shortcuts)
        assertEquals(2, opened.combos)
        assertEquals(1, opened.apps)
    }

    @Test
    fun aSharedProfilesFileNameIsSafe() {
        assertEquals("Thor-Pathfinder-profile-racing.json", Backup.profileFileName("racing"))
        assertEquals("Thor-Pathfinder-profile-My-games.json", Backup.profileFileName("My games!/"))
        assertEquals("Thor-Pathfinder-profile-profile.json", Backup.profileFileName("???"))
    }

    private fun header(kind: String) = JSONObject()
        .put("app", Backup.APP)
        .put("kind", kind)
        .put("format", Backup.FORMAT)
        .put("version", "1.0.0")
        .put("created", "2026-09-25T08:00:00-07:00")
}
