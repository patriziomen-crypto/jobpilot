package it.patrizio.jobpilot.ai;

import it.patrizio.jobpilot.jobs.JobOffer;

/** Testi delle istruzioni per l'AI, condivisi da tutti i fornitori. */
final class Prompts {

    private Prompts() {}

    /** Formato JSON della risposta dell'assistente per i fornitori senza risposte strutturate native. */
    static final String CHAT_JSON = """
            FORMATO DELLA RISPOSTA: rispondi SOLO con un oggetto JSON, senza testo attorno:
            {"reply": "testo per l'utente in italiano",
             "actions": [{"type": "CHANGE_STATUS|ANALYZE|GENERATE|START_SEARCH|LOGIN|OPEN",
                          "applicationId": "numero candidatura o stringa vuota",
                          "status": "nuovo stato o stringa vuota",
                          "site": "linkedin|indeed o stringa vuota",
                          "route": "pagina da aprire o stringa vuota"}]}
            Se non c'è nessuna azione da fare, "actions" è una lista vuota [].
            """;

    static String analyzeSystem(String profileText) {
        return """
                Sei un consulente di carriera esperto nel mercato IT italiano. Valuti offerte di lavoro per un candidato
                e rispondi SEMPRE in italiano, con onestà: niente incoraggiamenti vuoti.

                Regole:
                - Confronta ogni requisito dell'annuncio con il profilo. Un requisito è "soddisfatto" solo se il profilo
                  lo dimostra davvero; non dare per scontate competenze non elencate.
                - Un annuncio che chiede molti anni di esperienza o un livello senior va segnalato tra i red flag
                  e abbassa il punteggio.
                - Punteggio: 85-100 ottimo match, 65-84 buono, 45-64 possibile ma con lacune, sotto 45 sconsigliato.
                - Le parole chiave devono essere quelle testuali dell'annuncio, utili per CV e lettera.

                Profilo del candidato (fonte di verità):
                """ + profileText;
    }

    static String analyzeUser(JobOffer offer) {
        return """
                Analizza questa offerta di lavoro.

                Titolo: %s
                Azienda: %s
                Luogo: %s%s
                Fonte: %s

                Testo dell'annuncio:
                %s
                """.formatted(
                nullSafe(offer.getTitle()), nullSafe(offer.getCompany()), nullSafe(offer.getLocation()),
                offer.isRemote() ? " (remoto)" : "", offer.getSource(), offer.getDescription());
    }

    static String cvSystem(String profileText) {
        return """
                Sei un esperto di CV per profili IT in Italia. Riscrivi il CV del candidato per una specifica
                offerta di lavoro.

                REGOLA ASSOLUTA: puoi solo selezionare, riordinare e riformulare ciò che è nel profilo. È VIETATO
                aggiungere competenze, tecnologie, esperienze, titoli, anni di esperienza, progetti o risultati che non
                siano scritti nel profilo. Se l'offerta chiede qualcosa che il candidato non ha, semplicemente non lo citi.
                Ogni punto elenco deve derivare da una frase del profilo: niente attività o numeri inventati.

                Linee guida:
                - Usa le parole chiave dell'annuncio dove sono vere per il candidato.
                - Scrivi nella lingua dell'annuncio (italiano o inglese).
                - Frasi concrete, niente aggettivi vuoti ("dinamico", "proattivo").
                - Mantieni TUTTE le esperienze e i titoli di studio del profilo, cambiando solo ordine e formulazione.

                Profilo del candidato (fonte di verità):
                """ + profileText;
    }

    static String cvUser(JobOffer offer, String keywords) {
        return """
                Offerta: %s presso %s (%s)
                Parole chiave dell'annuncio: %s

                Testo dell'annuncio:
                %s
                """.formatted(nullSafe(offer.getTitle()), nullSafe(offer.getCompany()), nullSafe(offer.getLocation()),
                keywords == null ? "-" : keywords, offer.getDescription());
    }

    static String letterSystem(String profileText) {
        return """
                Scrivi lettere di presentazione per un candidato IT in Italia. Tono professionale ma umano,
                diretto, senza frasi fatte. Massimo 250 parole nel corpo. Nella lingua dell'annuncio.

                Struttura:
                1. Perché scrivo e perché proprio questa azienda/ruolo (un aggancio concreto preso dall'annuncio).
                2. Cosa porto: 2-3 elementi veri del profilo collegati ai requisiti dell'annuncio.
                3. Onestà sul proprio livello trasformata in punto di forza (voglia di imparare, esempi concreti).
                4. Disponibilità e chiusura.

                REGOLA ASSOLUTA: non inventare esperienze, competenze, progetti, numeri o risultati assenti dal profilo.
                Niente frasi come "ho integrato...", "ho gestito...", "ho realizzato..." se il profilo non lo dice
                esplicitamente. Se mancano esempi concreti, parla di formazione, motivazione e disponibilità a imparare.
                I paragrafi NON devono contenere saluti finali né la firma: vanno nei campi closing e firma separati.

                Profilo del candidato (fonte di verità):
                """ + profileText;
    }

    static String letterUser(JobOffer offer, String analysisSummary) {
        return """
                Offerta: %s presso %s (%s)
                Valutazione sintetica dell'offerta: %s

                Testo dell'annuncio:
                %s
                """.formatted(nullSafe(offer.getTitle()), nullSafe(offer.getCompany()), nullSafe(offer.getLocation()),
                analysisSummary == null ? "-" : analysisSummary, offer.getDescription());
    }

    /** Descrizione dello schema JSON atteso, per i fornitori che non hanno output strutturato nativo. */
    static final String ANALYSIS_JSON = """
            Rispondi SOLO con un oggetto JSON (senza testo attorno, senza ```), con esattamente questi campi:
            {"score": numero intero 0-100,
             "summary": "2-3 frasi in italiano: perché conviene o no candidarsi",
             "matchedRequirements": ["requisito soddisfatto", ...],
             "missingRequirements": ["requisito mancante", ...],
             "keywords": ["parola chiave dell'annuncio", ...],
             "redFlags": ["segnale di attenzione", ...],
             "language": "codice lingua dell'annuncio: it oppure en"}
            """;

    static final String CV_JSON = """
            Rispondi SOLO con un oggetto JSON (senza testo attorno, senza ```), con esattamente questi campi:
            {"headline": "titolo professionale adattato al ruolo",
             "summary": "profilo professionale di 3-5 righe riscritto per l'offerta",
             "experiences": [{"company": "...", "role": "...", "periodLabel": "...", "bullets": ["punto", ...]}],
             "educations": [{"institution": "...", "title": "...", "periodLabel": "...", "details": ["punto", ...]}],
             "skillGroups": [{"category": "...", "skills": ["...", ...]}]}
            """;

    static final String LETTER_JSON = """
            Rispondi SOLO con un oggetto JSON (senza testo attorno, senza ```), con esattamente questi campi:
            {"subject": "oggetto della lettera",
             "greeting": "formula di apertura, es. Gentile team di selezione di Acme,",
             "paragraphs": ["paragrafo 1", "paragrafo 2", "paragrafo 3"],
             "closing": "formula di chiusura, es. Cordiali saluti,"}
            """;

    static String shortAnswerSystem(String profileText) {
        return """
                Stai compilando un modulo di candidatura per conto del candidato. Rispondi alla domanda del modulo con
                una risposta BREVE (massimo 12 parole, oppure un numero, oppure una delle opzioni se sono indicate),
                basandoti SOLO sul profilo. Se la domanda chiede anni di esperienza, rispondi con un numero intero
                prudente (0 o 1 per un junior). Se non puoi rispondere dal profilo, rispondi esattamente: NON SO
                Nessuna spiegazione, solo la risposta.

                Profilo del candidato:
                """ + profileText;
    }

    static String shortAnswerUser(String question, java.util.List<String> options) {
        StringBuilder sb = new StringBuilder("Domanda del modulo: ").append(question).append('\n');
        if (options != null && !options.isEmpty()) {
            sb.append("Opzioni possibili (rispondi copiando esattamente una di queste):\n");
            for (String o : options) sb.append("- ").append(o).append('\n');
        }
        return sb.toString();
    }

    static String nullSafe(String value) {
        return value == null ? "-" : value;
    }
}
