package com.thorpathfinder.app

import com.thorpathfinder.app.Profiles.ORIGINAL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilesTest {

    @Test
    fun theFirstProfileKeepsTheOriginalShortcutsFile() {
        // An update must find every mapping exactly where it left it.
        assertEquals("shortcuts", Profiles.fileName(ORIGINAL))
        assertEquals("shortcuts.1", Profiles.fileName(1))
    }

    @Test
    fun aFreshInstallHasOneProfile() {
        assertEquals(listOf(ORIGINAL), Profiles.parseIds(null))
        assertEquals(listOf(ORIGINAL), Profiles.parseIds(""))
        assertEquals(listOf(ORIGINAL), Profiles.parseIds("nonsense"))
    }

    @Test
    fun theStoredOrderIsKeptAsItIs() {
        assertEquals(listOf(0, 2, 3), Profiles.parseIds("0,2,3"))
        assertEquals(listOf(0, 3, 1), Profiles.parseIds("0,3,1"))
        // The first profile is deletable once another is the main one, so a
        // list without it is a real list, not a broken one.
        assertEquals(listOf(2, 3), Profiles.parseIds("2,3"))
        assertEquals(listOf(0, 2), Profiles.parseIds("0,2,2"))
        assertEquals(listOf(0, 2), Profiles.parseIds("0, 2 ,oops"))
    }

    @Test
    fun cyclingGoesRoundTheListInOrder() {
        val ids = listOf(0, 2, 3)
        assertEquals(2, Profiles.after(ids, 0))
        assertEquals(3, Profiles.after(ids, 2))
        assertEquals(0, Profiles.after(ids, 3))
        // A profile that has been deleted since starts the list again.
        assertEquals(0, Profiles.after(ids, 9))
        assertEquals(ORIGINAL, Profiles.after(emptyList(), 0))
    }

    @Test
    fun enablingTheProfileAlreadyOnGoesBackToTheMainOne() {
        assertEquals(2, Profiles.toggled(active = 0, target = 2, main = 0))
        assertEquals(0, Profiles.toggled(active = 2, target = 2, main = 0))
        // The main profile toggles to itself, i.e. stays put.
        assertEquals(0, Profiles.toggled(active = 0, target = 0, main = 0))
    }

    @Test
    fun theWayBackFollowsWhicheverProfileTheUserMadeMain() {
        // With profile 3 as the main one, enabling it twice lands on 3, not 0.
        assertEquals(1, Profiles.toggled(active = 3, target = 1, main = 3))
        assertEquals(3, Profiles.toggled(active = 1, target = 1, main = 3))
        assertEquals(3, Profiles.toggled(active = 0, target = 0, main = 3))
    }

    @Test
    fun aDeletedProfilesFileIsNeverHandedToTheNextOne() {
        // Counter ahead of the list: ids 0 and 1 exist, 2 has been deleted.
        assertEquals(3, Profiles.nextId(listOf(0, 1), stored = 3))
        // A counter left behind by an older version still can't collide.
        assertEquals(4, Profiles.nextId(listOf(0, 3), stored = 1))
        assertEquals(1, Profiles.nextId(listOf(ORIGINAL), stored = 1))
    }

    @Test
    fun aNewProfileIsNamedAfterOneThatIsNotTaken() {
        assertEquals("Profile 2", Profiles.newName(listOf("Main profile")))
        assertEquals("Profile 3", Profiles.newName(listOf("Main profile", "Profile 2")))
        // The suggestion steps past a name the user typed themselves.
        assertEquals("Profile 4", Profiles.newName(listOf("Main profile", "profile 3")))
    }

    @Test
    fun aTypedNameIsTidiedAndNeverEmpty() {
        assertEquals("Gaming", Profiles.cleanName("  Gaming  ", "Profile 2"))
        assertEquals("Profile 2", Profiles.cleanName("   ", "Profile 2"))
        assertTrue(Profiles.cleanName("x".repeat(80), "Profile 2").length == Profiles.MAX_NAME)
    }
}
