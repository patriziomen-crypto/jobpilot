package it.patrizio.jobpilot.apply;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/apply")
public class ApplyController {

    private final ApplyService service;

    public ApplyController(ApplyService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public ApplyStatus status() {
        return service.status();
    }

    /** Avvia l'invio assistito per la candidatura indicata. */
    @PostMapping("/{applicationId}/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApplyStatus start(@PathVariable Long applicationId) {
        service.start(applicationId);
        return service.status();
    }

    @PostMapping("/confirm")
    public ApplyStatus confirm() {
        service.confirmSent();
        return service.status();
    }

    @PostMapping("/cancel")
    public ApplyStatus cancel() {
        service.cancel();
        return service.status();
    }

    @GetMapping("/settings")
    public Map<String, Boolean> settings() {
        return Map.of("autoSubmit", service.autoSubmit());
    }

    @PutMapping("/settings")
    public Map<String, Boolean> updateSettings(@RequestBody Map<String, Boolean> body) {
        service.setAutoSubmit(Boolean.TRUE.equals(body.get("autoSubmit")));
        return Map.of("autoSubmit", service.autoSubmit());
    }
}
