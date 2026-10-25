package com.mateuswilliam.financas.core

import java.nio.file.Files
import java.nio.file.Path

/** Avaliação privada, sem incluir mensagens no APK, nos relatórios JUnit ou na saída do console. */
object BankEvidenceCli {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1) { "Informe o arquivo TSV privado já conferido." }
        var count = 0
        Files.newBufferedReader(Path.of(args.single())).useLines { lines ->
            lines.forEachIndexed { index, line ->
                if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
                val fields = line.split('\t')
                require(fields.size == 11 && fields[0] == "REAL_ANONYMIZED") { "Linha ${index + 1}: procedência inválida." }
                val result = NotificationParser.parse(NotificationInput("evidence.local", fields[2], fields[1], fields[3], fields[4], 1_792_800_000_000L))
                fun matches(condition: Boolean, field: String) = check(condition) { "Linha ${index + 1}: divergência em $field. A mensagem não foi registrada na saída." }
                matches(NotificationParser.isSensitive(fields[3], fields[4]) == fields[9].toBooleanStrict(), "filtro sensível")
                matches(result.amountCents == fields[5].toLongOrNull(), "valor")
                matches(result.movement.name == fields[6], "movimentação")
                matches(result.method.name == fields[7], "meio de pagamento")
                matches(result.category.name == fields[8], "categoria")
                count++
            }
        }
        check(count > 0) { "Nenhuma evidência real foi avaliada." }
        println("Interpretação conferida em $count casos reais anonimizados. A entrega em segundo plano permanece uma avaliação distinta no aparelho.")
    }
}
