import com.ordia.app.domain.SensitiveSecretPatterns

// Veredicto de la auditoría c.1105 (familia c.1096 «(?i)» ASCII-only):
// la palabra-clave «n[uú]mero de seguro social» usa «(?i)» inline, que en la
// JVM NO casa la «Ú» de «NÚMERO» en MAYÚSCULAS. La sonda mide si eso deja
// fugar un NSS en caps-tilde... y NO lo hace: la alternativa ASCII redundante
// «seguro social» de la MISMA regex casa siempre que «número de seguro
// social» aparece, y el valor NSS (11 dígitos) cae en la ventana. La
// redundancia es carga-bearing (pines en SensitiveSecretPatternsTest). No se
// tocó «(?i)»: sin diferencia funcional medible, no hay cambio (menos es más).
fun main() {
    val cases = listOf(
        Triple("GAP1 caps-tilde NÚMERO", true, "MI NÚMERO DE SEGURO SOCIAL ES 12345678901"),
        Triple("GAP2 caps-tilde frase", true, "EL NÚMERO DE SEGURO SOCIAL DE MI MAMÁ ES 98765432109"),
        Triple("PIN1 minuscula hermana", true, "mi número de seguro social es 12345678901"),
        Triple("PIN2 caps SIN tilde", true, "MI NUMERO DE SEGURO SOCIAL ES 12345678901"),
        Triple("PIN3 alternativa ASCII", true, "MI SEGURO SOCIAL ES 12345678901"),
        Triple("PIN4 keyword ASCII caps", true, "MI PASAPORTE ES AB1234567"),
        Triple("NEG1 sin valor", false, "el número de seguro social no lo tengo"),
        Triple("NEG2 mencion casual", false, "hablamos del seguro social de la oficina"),
        Triple("NEG3 valor corto", false, "mi número de seguro social es 12345")
    )
    var fails = 0
    for ((id, expected, text) in cases) {
        val got = SensitiveSecretPatterns.containsPersonalIdentifier(text)
        val ok = got == expected
        if (!ok) fails++
        println("%-28s esperado=%-5s got=%-5s %s".format(id, expected, got, if (ok) "OK" else "FALLO"))
    }
    println(if (fails == 0) "SONDA: 9/9 OK" else "SONDA: $fails fallos")
}
