package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class BankNotificationFixturesTest(private val provenance: String, private val bank: String, private val caseId: String,
    private val title: String, private val body: String, private val cents: String, private val movement: String,
    private val method: String, private val category: String, private val sensitive: String, private val evidence: String) {
    @Test fun recognizedFieldsMatchTheReviewedFixtureWithoutClaimingBankCertification() {
        assertTrue(provenance in setOf("SYNTHETIC", "REAL_ANONYMIZED"))
        assertTrue(evidence.isNotBlank())
        assertEquals("true" == sensitive, NotificationParser.isSensitive(title, body))
        val result = NotificationParser.parse(NotificationInput("fixture.local", caseId, bank, title, body, 1_792_800_000_000L))
        assertEquals(cents.toLongOrNull(), result.amountCents)
        assertEquals(Movement.valueOf(movement), result.movement)
        assertEquals(PaymentMethod.valueOf(method), result.method)
        assertEquals(Category.valueOf(category), result.category)
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{1}/{2} [{0}]")
        fun cases(): Collection<Array<Any>> = requireNotNull(BankNotificationFixturesTest::class.java.getResourceAsStream("/bank-notifications.tsv"))
            .bufferedReader().use { reader -> reader.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
                line.split('\t').also { require(it.size == 11) }.map { it as Any }.toTypedArray()
            } }
    }
}
