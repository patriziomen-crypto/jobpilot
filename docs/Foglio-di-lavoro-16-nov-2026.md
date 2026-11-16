# JobPilot – Foglio di lavoro aggiornato al 16 novembre 2026

Stato a fine sessione: **l'app è completa e funzionante**, l'eseguibile sul Desktop (`JobPilot.exe`) è l'ultima versione, tutto è salvato nel repository git (ultimo commit `77194ea`). Test automatici: 19 su 19 passano.

## 1. Cosa è stato fatto oggi (in ordine)

| Ora | Cosa | Dove |
|---|---|---|
| 12:06 | **Più persone**: ogni persona ha profilo, candidature e ricerca propri; selettore in alto a destra; "Nuova persona" con copia opzionale di competenze e risposte pronte | commit `62e51fe` |
| 12:06 | **Fornitori AI multipli**: Claude, Groq, Gemini, OpenAI, GitHub Models, Mistral, OpenRouter, Ollama; l'app prova il n.1 e passa al successivo se non risponde; **modalità base senza AI** sempre disponibile | `62e51fe` |
| 12:19 | Documentazione (PIANO.md, README) aggiornata; permessi dei comandi di routine | `26140f4` |
| 12:38 | **Revisione sicurezza e qualità** (agenti ECC): chiavi AI protette (URL solo https, chiave azzerata se cambia l'indirizzo, file impostazioni leggibile solo dall'utente), CORS ristretto, filtro Host/CSRF, SSRF bloccata, timeout AI, chiamate AI fuori transazione, LinkedIn su 4 pagine, lettura pannello Indeed corretta | `e8c4033` |
| 12:51 | Elenco modelli dal fornitore ("Modelli disponibili"), preset Groq aggiornato (`openai/gpt-oss-120b`), anni di esperienza riconosciuti solo se richiesti, prompt anti-invenzione più severi | `8b57911` |
| 12:55 | Pagina mai in cache (niente pulsanti morti dopo un aggiornamento) | `82c3b00` |
| 13:07 | **Impostazioni rifatte**: elenco dei fornitori salvati con stato (funziona / errore / mai provato), interruttore attivo/disattivo, frecce per l'ordine, "Prova" | `e92700a` |
| 13:19 | **Invio assistito**: pulsante "Compila la candidatura nel browser"; l'app apre Edge, clicca Candidatura semplice (LinkedIn) o Candidati ora (Indeed), carica il CV, compila campi e domande con le Risposte pronte e l'AI, **si ferma prima di Invia**; "Ho inviato" / "Annulla"; interruttore "Invio automatico senza conferma" (sconsigliato) | `e2adf07` |
| 13:21 | Correzione nome/cognome nei moduli; lo script di build chiude l'exe prima di ricostruirlo | `ba93672` |
| 15:52 | **Account collegati** (pagina Ricerca): stato Connesso / Non connesso per LinkedIn e Indeed letto dai cookie del browser dell'app, pulsanti Accedi / Apri / Disconnetti / Verifica adesso, verifica automatica all'avvio; **una sola finestra di accesso** per entrambi i siti (prima le due finestre si bloccavano a vicenda) | `ecce73d` |
| 16:41 | **Assistente in chat** (pulsante in basso a destra su ogni pagina): conosce l'app, il profilo, le candidature, i fornitori e gli account; risponde a domande ed esegue azioni (cambiare stato, analizzare, generare CV e lettera, avviare la ricerca, aprire pagine); usa il fornitore AI n.1; senza AI dà istruzioni fisse. Corretto l'errore iniziale (profilo letto fuori transazione) | `77194ea` |

## 2. Cosa è stato verificato davvero

- Analisi, CV e lettera PDF in modalità base e con **Groq** (gratuito) su annunci reali.
- Ricerca LinkedIn: annunci veri importati senza account.
- Protezioni di sicurezza provate una per una (403 senza header, 404 persona non valida, 400 URL http, chiave azzerata al cambio URL, permessi del file).
- Account collegati: LinkedIn risulta **Connesso** (il tuo accesso è memorizzato), Indeed **Non connesso**. Finestra unica con due schede provata.
- Assistente: risposte corrette con Groq (elenco candidature da seguire, apertura pagine, istruzioni per Claude). Groq **non** ha esaurito le richieste: l'errore visto era il bug poi corretto.
- Eseguibile ricostruito e avviato con successo dopo ogni modifica.

## 3. Cosa NON è ancora verificato (serve il tuo intervento)

1. **Accesso a Indeed**: devi entrare una volta con il tuo account dalla sezione Account collegati → Accedi. Dopo il login, se lo stato resta "Non connesso", dimmelo: cambio il cookie che l'app controlla.
2. **Invio assistito sui siti veri**: scritto sulle strutture attuali di LinkedIn e Indeed ma mai provato con un account. Prova con la candidatura **#3 MBDA** (ha già CV e lettera) e riporta cosa succede.
3. **Claude**: non ancora collegato (serve una chiave API con credito da console.anthropic.com; l'abbonamento Claude.ai non vale per le API). Istruzioni passo passo nel capitolo 5.

## 4. Come si usa l'app (riassunto)

- **Dashboard**: candidature in colonne per stato, trascinabili. Clic su una card per aprirla.
- **Candidatura**: passo 1 analisi (punteggio 0-100, requisiti, red flag) → passo 2 "Genera CV e lettera" → passo 3 "Compila la candidatura nel browser" oppure "Apri annuncio"; alla fine "Ho inviato". Promemoria a 7 giorni automatico.
- **Ricerca automatica**: parole chiave una per riga, zona, giorni; "Cerca ora"; opzione "Cerca da sola ogni mattina alle 8". In alto "Account collegati".
- **Importa**: incolla indirizzo o testo di un annuncio. C'è anche il bookmarklet in `bookmarklet/`.
- **Statistiche**: totali, tasso di risposta, promemoria.
- **Profilo**: CV master e Risposte pronte (usate dall'invio assistito per i moduli).
- **Impostazioni** (ingranaggio): fornitori AI con stato, ordine, prova; interruttore invio automatico.
- **Assistente** (in basso a destra): domande e azioni in linguaggio naturale.

## 5. Collegare Claude (quando potrai)

1. console.anthropic.com → accedi (va bene la stessa email di Claude.ai).
2. Settings → **Billing**: carica un credito (bastano 5 €; un'analisi completa con CV e lettera costa circa 5 centesimi).
3. Settings → **API keys → Create key**, nome "JobPilot", copia la chiave `sk-ant-…` (si vede una volta sola).
4. JobPilot → ingranaggio → **Aggiungi fornitore AI → Claude (Anthropic)** → incolla la chiave → Salva → Prova.
5. Con le frecce metti Claude al **numero 1**; Groq resta come riserva.

## 6. Dove sono le cose

| Cosa | Percorso |
|---|---|
| Progetto (git) | `C:\Users\patri\Desktop\Gestione-Linkedin` |
| **Portfolio su GitHub** | https://github.com/patriziomen-crypto/jobpilot (pubblico; storia pulita senza CV, telefono ed email; la storia completa resta sul PC nel ramo `storia-locale`) |
| Eseguibile | `dist\JobPilot\JobPilot.exe` (collegamento `JobPilot` sul Desktop) |
| Ricostruire l'exe dopo una modifica | `.\scripts\build-desktop.ps1` (chiude l'app, ricompila frontend e backend, rifà l'exe) |
| Impostazioni e chiavi AI | `%LOCALAPPDATA%\JobPilot\jobpilot.properties` (leggibile solo dal tuo utente) |
| Browser dell'app (accessi LinkedIn/Indeed) | `%LOCALAPPDATA%\JobPilot\browser-profile` |
| PDF generati | `output\<persona>\<data_azienda_ruolo>\` |
| Database | MySQL 9.7, servizio `MySQL97`, porta 3307, db `jobpilot` |
| Piano completo | `docs\PIANO.md` e `docs\Piano-JobPilot.pdf` |
| Test | `backend\mvnw test` (19 test) e `frontend: npx ng build` |

## 7. Prossimi passi consigliati

1. Accedi a Indeed dall'app e lancia "Cerca ora" con Indeed attivo.
2. Prova l'invio assistito su MBDA (#3) e dimmi dove si ferma.
3. Collega Claude quando avrai la chiave.
4. Idee rimaste nel cassetto (non urgenti): avvisi via email, cifratura DPAPI delle chiavi, estensione browser al posto del bookmarklet, PIANO.md con la Fase 5 (invio assistito e assistente).
