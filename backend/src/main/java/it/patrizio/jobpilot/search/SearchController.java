package it.patrizio.jobpilot.search;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final JobSearchService service;

    public SearchController(JobSearchService service) {
        this.service = service;
    }

    @GetMapping("/settings")
    public SearchSettings settings() {
        return service.getSettings();
    }

    @PutMapping("/settings")
    public SearchSettings update(@RequestBody SearchSettings settings) {
        return service.updateSettings(settings);
    }

    @GetMapping("/status")
    public SearchStatus status() {
        return service.status();
    }

    @PostMapping("/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SearchStatus run() {
        service.start();
        return service.status();
    }

    @PostMapping("/stop")
    public SearchStatus stop() {
        service.stop();
        return service.status();
    }
}
