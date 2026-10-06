package cz.petrkyncl.bazosiphony

import cz.petrkyncl.bazosiphony.domain.Grade
import cz.petrkyncl.bazosiphony.domain.Issue
import cz.petrkyncl.bazosiphony.domain.estimateCondition
import cz.petrkyncl.bazosiphony.domain.listingIssue
import cz.petrkyncl.bazosiphony.domain.modelLabel
import cz.petrkyncl.bazosiphony.domain.parseIphone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DomainTest {
    private fun model(t: String) = parseIphone(t)?.let { modelLabel(it) to it.storageGb }

    @Test
    fun parsesModels() {
        assertEquals("iPhone 17 Pro Max" to null, model("Prodám I PHONE 17 PRO MAX"))
        assertEquals("iPhone SE (2022)" to 64, model("iPhone SE 2022 64GB"))
        assertEquals("iPhone 13 Mini" to 1024, model("iphone 13 mini 1TB"))
        assertEquals("iPhone 15 Pro" to 128, model("IPhone 15 pro 128Gb"))
        assertEquals("iPhone 16e" to 128, model("iPhone 16e 128GB e-sim"))
        assertEquals("iPhone XS Max" to 256, model("Apple iPhone XS Max 256GB"))
        assertEquals("iPhone 14 Plus" to 128, model("iPhone 14+ 128gb"))
        assertEquals("iPhone Air" to 256, model("Iphone 17 Air 256 GB"))
        assertEquals("iPhone 12 Pro Max" to 128, model("iPhone 12 Pro Max 128 GB max stav"))
        assertEquals("iPhone 18 Pro Max" to 2048, model("Apple iPhone 18 Pro Max 2TB"))
        assertNull(parseIphone("Samsung Galaxy S24"))
    }

    private fun grade(t: String, d: String) = estimateCondition(t, d).grade

    @Test
    fun estimatesCondition() {
        assertEquals(Grade.A, grade("iPhone 15 Pro", "Telefon je bez poškození, škrábanců a oděrek, jako nový. Kondice baterie 93%."))
        assertEquals(Grade.B, grade("iPhone 17 Pro Max", "Pouze na jednom růžku je drobný mikroškrábanec."))
        assertEquals(Grade.APLUS, grade("iPhone 14 Nový nerozbalený", "Zapečetěný, nepoužitý."))
        assertEquals(Grade.D, grade("iPhone 13 prasklý displej", "Jinak funkční."))
        assertEquals(Grade.C, grade("iPhone 11", "Telefon má hlubší škrábance na rámečku."))
        assertEquals(Grade.B, grade("iPhone 15", "Prasklé ochranné sklo, telefon bez prasklin."))
        assertEquals(Grade.A, grade("iPhone 15 Pro", "Jako nový. Jediné co je trochu prasklé sklíčko, ale telefon je netknutý."))
        assertEquals(Grade.D, grade("iPhone 14 Pro", "Nefunguje Face ID, jinak ok."))
        assertEquals(Grade.D, grade("iPhone 16", "Telefon nemá žádné vady, ale prasklá záda."))
    }

    @Test
    fun readsBattery() {
        assertEquals(93, estimateCondition("x", "Kondice baterie 93%").batteryPct)
        assertNull(estimateCondition("x", "když klesne kondice baterie pod 80% do března, vymění ji").batteryPct)
    }

    @Test
    fun detectsNoise() {
        assertEquals(Issue.SOLD, listingIssue("PRODÁNO iPhone 15", ""))
        assertEquals(Issue.BUY_REQUEST, listingIssue("Koupím iPhone 13", ""))
        assertEquals(Issue.NOT_PHONE, listingIssue("Kryt na iPhone 15 Pro", ""))
        assertNull(listingIssue("iPhone 15 Pro + kryt", ""))
        assertEquals(Issue.NOT_PHONE, listingIssue("Apple Watch 9", ""))
        assertEquals(Issue.RESERVATION_SALE, listingIssue("Rezervace iPhone 18 Pro", ""))
    }
}
