package app.margem.core

import java.time.ZoneId

/** Fontes de tempo do sistema; injetável para testes. */
interface Clock {
    /** Relógio de parede (o usuário pode alterar). */
    fun wallMs(): Long
    /** Monotônico desde o boot, inclui sono; imune a mudanças manuais. */
    fun elapsedMs(): Long
    fun bootCount(): Int
    fun zone(): ZoneId
}
