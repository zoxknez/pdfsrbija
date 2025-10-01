package stirling.software.SPDF.controller.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

import stirling.software.SPDF.service.SrbProfileService;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class SrbAdminApiController {

    private final SrbProfileService profileService;

    @GetMapping(path = "/profile", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public SrbProfileService.SrbProfile get() {
        return profileService.load();
    }

    @PostMapping(
            path = "/profile",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public SrbProfileService.SrbProfile save(@RequestBody SrbProfileService.SrbProfile body)
            throws Exception {
        return profileService.save(body);
    }
}
