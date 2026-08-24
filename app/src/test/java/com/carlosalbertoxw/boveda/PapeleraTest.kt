package com.carlosalbertoxw.boveda

import com.carlosalbertoxw.boveda.datos.Papelera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * El plazo de la papelera.
 *
 * Es aritmetica de fechas, que es justo donde se cuelan los errores que nadie
 * ve hasta que borran algo que todavia se podia recuperar. Se prueba sin base
 * de datos y sin esperar treinta dias porque el reloj entra como parametro.
 */
class PapeleraTest {

    private val ahora = 1_700_000_000_000L
    private val dia = TimeUnit.DAYS.toMillis(1)

    @Test
    fun `lo que se acaba de borrar tiene el plazo entero por delante`() {
        assertEquals(Papelera.DIAS_RETENCION, Papelera.diasRestantes(ahora, ahora))
    }

    @Test
    fun `el ultimo dia sigue contando como un dia`() {
        // Borrado hace veintinueve dias y medio: queda medio dia. Se redondea
        // hacia arriba porque todavia se puede recuperar, y decir "0 dias" de
        // algo que aun esta ahi invita a no intentarlo.
        val casiVencido = ahora - (Papelera.DIAS_RETENCION * dia) + dia / 2

        assertEquals(1, Papelera.diasRestantes(casiVencido, ahora))
    }

    @Test
    fun `al cumplirse el plazo no queda nada`() {
        val justo = ahora - Papelera.DIAS_RETENCION * dia

        assertEquals(0, Papelera.diasRestantes(justo, ahora))
        assertEquals(0, Papelera.diasRestantes(justo - dia, ahora))
    }

    @Test
    fun `la cuenta baja de uno en uno`() {
        val esperados = (1..Papelera.DIAS_RETENCION).map { diasPasados ->
            Papelera.diasRestantes(ahora - diasPasados * dia, ahora)
        }

        assertEquals((1 until Papelera.DIAS_RETENCION).toList().reversed() + listOf(0), esperados)
    }

    @Test
    fun `el limite de la purga deja fuera lo que aun no cumplio el plazo`() {
        val limite = Papelera.limite(ahora)

        // La consulta borra lo que tenga eliminadoEn <= limite. Lo de hace
        // veintinueve dias tiene que sobrevivir; lo de hace treinta y uno, no.
        assertTrue(ahora - 29 * dia > limite)
        assertTrue(ahora - 31 * dia < limite)
        assertEquals(ahora - Papelera.DIAS_RETENCION * dia, limite)
    }
}
