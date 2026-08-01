package com.ordia.app.domain

import com.ordia.app.data.preferences.InterfaceMode

/**
 * Valor seguro para el modo de interfaz.
 *
 * Ante valores ausentes o desconocidos (preferencia corrupta, copia de seguridad
 * antigua, valor inventado) se usa [InterfaceMode.ORGANIZED], un modo válido que
 * siempre permite continuar: el onboarding nunca debe quedar atrapado por un dato
 * inválido.
 */
fun resolveInterfaceMode(raw: String?): InterfaceMode =
    InterfaceMode.entries.firstOrNull { it.name == raw } ?: InterfaceMode.ORGANIZED
