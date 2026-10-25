plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { jvmToolchain(17) }
dependencies { testImplementation("junit:junit:4.13.2") }

// Os arquivos privados são fornecidos por caminho; não entram no conjunto público de recursos.
val bankEvidenceDirectory = providers.gradleProperty("bankEvidenceDirectory")
val verifyPrivateBankEvidence by tasks.registering(Exec::class) {
    doFirst {
        val directory = bankEvidenceDirectory.orNull?.let { rootProject.file(it) }
            ?: throw GradleException("Informe -PbankEvidenceDirectory=casos-locais com notificacoes.tsv e evidencias.json revisados.")
        commandLine("python3", rootProject.file("scripts/verify-bank-fixtures.py"), "--require-real",
            "--fixtures", directory.resolve("notificacoes.tsv"), "--evidence", directory.resolve("evidencias.json"))
    }
}
tasks.register<JavaExec>("validateBankEvidence") {
    group = "verification"
    description = "Confere metadados e executa o interpretador contra evidências privadas anonimizadas."
    dependsOn(verifyPrivateBankEvidence, tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.mateuswilliam.financas.core.BankEvidenceCli")
    doFirst { args(rootProject.file(bankEvidenceDirectory.get()).resolve("notificacoes.tsv").absolutePath) }
}
