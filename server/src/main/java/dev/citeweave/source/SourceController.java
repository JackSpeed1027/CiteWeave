package dev.citeweave.source;

import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/sources")
class SourceController {
    record PasteRequest(String title, String content) {}

    private final SourceService service;

    SourceController(SourceService service) {
        this.service = service;
    }

    @GetMapping
    List<SourceRepository.Summary> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    SourceRepository.Detail detail(@PathVariable long id) {
        return service.find(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<SourceRepository.Detail> paste(@RequestBody PasteRequest request) {
        return created(service.paste(request.title(), request.content()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<SourceRepository.Detail> upload(@RequestParam MultipartFile file,
                                                   @RequestParam(required = false) String title) {
        return created(service.upload(title, file));
    }

    private ResponseEntity<SourceRepository.Detail> created(SourceRepository.Detail detail) {
        return ResponseEntity.created(URI.create("/api/sources/" + detail.source().id())).body(detail);
    }
}
