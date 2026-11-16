package it.patrizio.jobpilot.chat;

import it.patrizio.jobpilot.ai.ChatReply;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Senza fornitori AI l'assistente deve comunque dare le istruzioni di base giuste. */
class ChatServiceTest {

    @Test
    void senzaAiSpiegaComeCollegareClaude() {
        String r = ChatService.basicAnswer("Come collego Claude?", "nessun fornitore attivo");
        assertTrue(r.contains("console.anthropic.com"));
        assertTrue(r.contains("Impostazioni"));
    }

    @Test
    void senzaAiSpiegaGliAccessi() {
        String r = ChatService.basicAnswer("non riesco ad accedere a Indeed", "nessun fornitore attivo");
        assertTrue(r.contains("Account collegati"));
    }

    @Test
    void senzaAiRispostaGenerica() {
        String r = ChatService.basicAnswer("ciao", "nessun fornitore attivo");
        assertTrue(r.contains("Posso spiegarti"));
    }

    @Test
    void azioniNulleDiventanoListaVuota() {
        assertEquals(0, new ChatReply("ok", null).safeActions().size());
    }
}
