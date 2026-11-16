package it.patrizio.jobpilot.profile;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    /** Profilo della persona attiva (header X-Profile-Id). */
    @GetMapping("/profile")
    public ProfileDto get() {
        return service.getProfileDto();
    }

    @PutMapping("/profile")
    public ProfileDto update(@Valid @RequestBody ProfileDto dto) {
        return service.update(dto);
    }

    /** Elenco delle persone gestite. */
    @GetMapping("/profiles")
    public List<ProfileService.ProfileSummary> list() {
        return service.list();
    }

    @PostMapping("/profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileService.ProfileSummary create(@RequestBody ProfileService.NewProfileRequest request) {
        return service.create(request);
    }

    @DeleteMapping("/profiles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
