package it.patrizio.jobpilot.applications;

import it.patrizio.jobpilot.documents.DocumentService;
import it.patrizio.jobpilot.jobs.JobImportRequest;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService service;
    private final DocumentService documents;

    public ApplicationController(ApplicationService service, DocumentService documents) {
        this.service = service;
        this.documents = documents;
    }

    public record StatusChange(@NotNull ApplicationStatus status) {}

    /** Cosa generare: per default sia CV sia lettera. */
    public record GenerateRequest(Boolean cv, Boolean letter) {}

    public record NotesChange(String notes, LocalDate followUpAt) {}

    @GetMapping
    public List<ApplicationDto> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ApplicationDto get(@PathVariable Long id) {
        return service.get(id);
    }

    /** Importa un'offerta (URL e/o testo) e crea la candidatura. */
    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationDto importJob(@RequestBody JobImportRequest request) {
        return service.importAndCreate(request);
    }

    @PostMapping("/{id}/analyze")
    public ApplicationDto analyze(@PathVariable Long id) {
        return service.analyze(id);
    }

    /** Genera CV su misura e/o lettera di presentazione (testo + PDF) con Claude. */
    @PostMapping("/{id}/generate")
    public ApplicationDto generate(@PathVariable Long id, @RequestBody(required = false) GenerateRequest request) {
        boolean cv = request == null || request.cv() == null || request.cv();
        boolean letter = request == null || request.letter() == null || request.letter();
        documents.generate(id, cv, letter);
        return service.get(id);
    }

    @PatchMapping("/{id}/status")
    public ApplicationDto changeStatus(@PathVariable Long id, @RequestBody StatusChange change) {
        return service.changeStatus(id, change.status());
    }

    @PatchMapping("/{id}/notes")
    public ApplicationDto updateNotes(@PathVariable Long id, @RequestBody NotesChange change) {
        return service.updateNotes(id, change.notes(), change.followUpAt());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
