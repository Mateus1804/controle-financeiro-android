package com.mateuswilliam.financas.core

enum class CaptureIssue(val message: String, val action: String) {
    CONSENT("Ative as sugestões de transações a partir das notificações. O cadastro manual continua disponível.", "Conhecer recurso"),
    DISABLED("O preenchimento por notificações está pausado. O cadastro manual continua disponível.", "Ativar captura"),
    APPS("Escolha os aplicativos que podem sugerir transações.", "Escolher aplicativos"),
    ACCESS("Ative o acesso às notificações para receber sugestões de transações. O cadastro manual continua disponível.", "Autorizar acesso"),
    CONNECTION("Acesso autorizado. O serviço ainda está desconectado; confira o estado em Ajustes.", "Ver ajustes"),
    READY("Captura ativa para os aplicativos escolhidos.", "Ver ajustes");

    companion object {
        fun evaluate(consented: Boolean, enabled: Boolean, hasApps: Boolean, hasAccess: Boolean, connected: Boolean): CaptureIssue = when {
            !consented -> CONSENT
            !enabled -> DISABLED
            !hasApps -> APPS
            !hasAccess -> ACCESS
            !connected -> CONNECTION
            else -> READY
        }
    }
}
