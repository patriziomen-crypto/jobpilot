package it.patrizio.jobpilot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Trasforma le eccezioni in risposte JSON { "message": "..." } leggibili dal frontend. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(JobPilotException.class)
    public ResponseEntity<Map<String, String>> handleJobPilot(JobPilotException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("Errore applicativo: {}", ex.getMessage(), ex);
        }
        return ResponseEntity.status(ex.getStatus()).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("Dati non validi");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    /** File dell'app non trovato (tipicamente una pagina vecchia in cache che chiede file di una versione precedente). */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleMissingResource(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        log.warn("Risorsa non trovata: {} (se succede dopo un aggiornamento, ricaricare la pagina con Ctrl+F5)", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "File non trovato: ricarica la pagina (Ctrl+F5)"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        log.error("Errore inatteso", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Errore inatteso nel programma: i dettagli sono nel log"));
    }
}
