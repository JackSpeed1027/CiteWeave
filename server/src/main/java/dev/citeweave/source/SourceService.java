package dev.citeweave.source;

import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
class SourceService {
    private final SourceRepository repository;
    private final SourceParser parser;

    SourceService(SourceRepository repository, SourceParser parser) {
        this.repository = repository;
        this.parser = parser;
    }

    List<SourceRepository.Summary> list() {
        return repository.list();
    }

    SourceRepository.Detail find(long id) {
        return repository.find(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到这份资料。"));
    }

    SourceRepository.Detail paste(String title, String content) {
        return repository.insert(validTitle(title), "TEXT", null, parser.text(content));
    }

    SourceRepository.Detail upload(String title, MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String filename = originalName == null ? "" : originalName.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1).strip();
        if (file.isEmpty() || filename.isBlank() || filename.length() > 255
                || filename.indexOf('.') <= 0 || filename.indexOf(0) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择具有有效文件名的非空文件。");
        }
        String resolvedTitle = title == null || title.isBlank()
                ? filename.substring(0, filename.lastIndexOf('.')) : title;
        resolvedTitle = validTitle(resolvedTitle);
        try {
            var parsed = parser.file(filename, file.getBytes());
            return repository.insert(resolvedTitle, parsed.kind(), filename, parsed.content());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取上传的文件。", exception);
        }
    }

    private String validTitle(String title) {
        String value = title == null ? "" : title.strip();
        if (value.isEmpty() || value.length() > 160 || value.indexOf(0) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标题长度须为 1–160 字符。");
        }
        return value;
    }
}
