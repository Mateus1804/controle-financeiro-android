package com.mateuswilliam.financas.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import org.junit.Assert.*

/**
 * Compose 1.9.5 reconstrói o parágrafo semântico com a largura disponível, que pode
 * ser maior que a caixa do texto. Confere as linhas, não esse espaço vazio.
 * Estes controles usam alinhamento inicial; não usar para texto centralizado.
 */
internal fun SemanticsNodeInteraction.assertStartAlignedTextFits(label: String, singleLine: Boolean = false): SemanticsNodeInteraction {
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
        val layouts = mutableListOf<TextLayoutResult>()
        assertTrue("$label precisa informar seu texto", action(layouts))
        assertEquals("$label precisa ter um resultado de texto", 1, layouts.size)
        val layout = layouts.single()
        val alignment = layout.layoutInput.style.textAlign
        assertTrue("Verificação exige alinhamento inicial", alignment == TextAlign.Start || alignment == TextAlign.Left || alignment == TextAlign.Unspecified)
        assertTrue("$label precisa ter linhas", layout.lineCount > 0)
        if (singleLine) assertEquals("$label deve caber inteiro em uma linha", 1, layout.lineCount)
        assertFalse("$label não pode perder linhas", layout.multiParagraph.didExceedMaxLines)
        repeat(layout.lineCount) { line ->
            assertFalse("$label não pode usar reticências", layout.isLineEllipsized(line))
            assertTrue("$label ultrapassa a borda esquerda", layout.getLineLeft(line) >= 0f)
            assertTrue("$label ultrapassa a borda direita", layout.getLineRight(line) <= layout.size.width.toFloat())
            assertTrue("$label ultrapassa a borda superior", layout.getLineTop(line) >= 0f)
            assertTrue("$label ultrapassa a borda inferior", layout.getLineBottom(line) <= layout.size.height.toFloat())
        }
    }
    return this
}
