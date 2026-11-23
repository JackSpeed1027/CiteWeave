package dev.citeweave.source;

import com.fasterxml.jackson.databind.JsonNode;
import dev.citeweave.web.ApiProblems;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/sources")
class SourceController {
    record PasteRequest(String title, String content) {}
    record Favorite(boolean favorited) {}

    private final SourceService service;

    SourceController(SourceService service) {
        this.service = service;
    }

    @GetMapping
    List<SourceRepository.Summary> list(@RequestParam(defaultValue = "false") boolean favorite) {
        return favorite ? service.listFavorites() : service.list();
    }

    /** 读取资料的个人收藏状态。 */
    @GetMapping("/{id}/favorite")
    Favorite favorite(@PathVariable long id) {
        return new Favorite(service.favorite(id));
    }

    /** 用明确的 JSON 布尔值设置状态，使超时后的同值重试安全。 */
    @PutMapping(path = "/{id}/favorite", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> favorite(@PathVariable long id, @RequestBody JsonNode body) {
        var value = body.path("favorited");
        if (!value.isBoolean()) {
            return ResponseEntity.badRequest().body(ApiProblems.create(
                    HttpStatus.BAD_REQUEST, "SOURCE_FAVORITE_INVALID", "favorited 必须是 JSON 布尔值。"));
        }
        return ResponseEntity.ok(new Favorite(service.setFavorite(id, value.booleanValue())));
    }

    @GetMapping("/{id}")
    SourceRepository.Detail detail(@PathVariable long id) {
        return service.find(id);
    }

    @GetMapping("/{id}/transcript")
    List<TranscriptParser.Segment> transcript(@PathVariable long id) {
        return service.transcript(id);
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
