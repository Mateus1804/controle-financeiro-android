package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test

class CaptureSetupTest {
    @Test fun `consentimento e ativacao precedem acesso do sistema`() {
        assertEquals(CaptureIssue.CONSENT, CaptureIssue.evaluate(false, false, true, true, true))
        assertEquals(CaptureIssue.DISABLED, CaptureIssue.evaluate(true, false, true, true, true))
    }
    @Test fun `selecao acesso e conexao sao condicoes diferentes`() {
        assertEquals(CaptureIssue.APPS, CaptureIssue.evaluate(true, true, false, true, true))
        assertEquals(CaptureIssue.ACCESS, CaptureIssue.evaluate(true, true, true, false, true))
        assertEquals(CaptureIssue.CONNECTION, CaptureIssue.evaluate(true, true, true, true, false))
    }
    @Test fun `somente configuracao completa indica captura ativa`() {
        assertEquals(CaptureIssue.READY, CaptureIssue.evaluate(true, true, true, true, true))
    }
}
