package cl.nexus.eq

/** Reporte de texto para pegar en el grupo de testers. Sin datos personales: solo equipo y estado del DSP. */
object TesterReport {
    fun build(
        appVersion: String,
        device: String,
        app: AppState,
        links: Map<Int, AppLink>,
        log: List<String>,
    ): String = buildString {
        appendLine("MARAKES TESTER REPORT")
        appendLine("NEXUS.EQ v$appVersion")
        appendLine("Equipo: $device")
        val eq = app.current
        appendLine("Estado: ${if (app.enabled) "ON" else "BYPASS"} · salida ${app.route.label} · preset ${eq.presetName}")
        appendLine("Bandas: ${eq.layout.count} · skin ${app.skinId} · modo gráfico ${if (app.vizDelay > 0) "${app.vizDelay} s" else "apagado"}")
        appendLine(
            "Preamp aplicado: ${"%.1f".format(java.util.Locale.US, EqMath.effectivePreamp(eq))} dB" +
                " · limitador ${if (eq.limiter) "ON" else "OFF"} · global ${if (app.globalMode) "ON" else "OFF"}"
        )
        appendLine("Apps detectadas:")
        if (links.isEmpty()) appendLine(" - ninguna (reproduce música y abre el ecualizador desde la app)")
        links.values.forEach {
            appendLine(" - ${AppNames.label(it.pkg)} (${it.pkg}): ${if (it.ok) "ENLAZADA" else "ERROR DSP"}")
        }
        appendLine("Eventos recientes:")
        if (log.isEmpty()) appendLine(" - sin eventos") else log.takeLast(12).forEach { appendLine(" - $it") }
    }.trimEnd()
}
