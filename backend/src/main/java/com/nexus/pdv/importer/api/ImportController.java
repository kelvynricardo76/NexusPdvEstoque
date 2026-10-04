package com.nexus.pdv.importer.api;

import com.nexus.pdv.importer.api.ImportDtos.ConfirmRequest;
import com.nexus.pdv.importer.api.ImportDtos.FieldInfo;
import com.nexus.pdv.importer.api.ImportDtos.ImportJobSummary;
import com.nexus.pdv.importer.api.ImportDtos.ImportReport;
import com.nexus.pdv.importer.api.ImportDtos.MappingRequest;
import com.nexus.pdv.importer.api.ImportDtos.UploadResponse;
import com.nexus.pdv.importer.api.ImportDtos.ValidationResponse;
import com.nexus.pdv.importer.application.ImportService;
import com.nexus.pdv.importer.domain.ImportEntityType;
import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.shared.access.RequiresPermission;
import com.nexus.pdv.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Importação de dados — exige a feature DATA_IMPORT (via IMPORT_EXECUTE). */
@Tag(name = "Importação")
@RestController
@RequestMapping("/api/imports")
@RequiresPermission(Permission.IMPORT_EXECUTE)
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @GetMapping
    public PageResponse<ImportJobSummary> list(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(importService.list(pageable));
    }

    @GetMapping("/fields/{type}")
    public List<FieldInfo> fields(@PathVariable ImportEntityType type) {
        return importService.fields(type);
    }

    @GetMapping("/templates/{type}")
    public ResponseEntity<byte[]> template(@PathVariable ImportEntityType type) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("modelo-" + type.name().toLowerCase() + ".csv").build().toString())
                .body(importService.template(type));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResponse upload(@RequestPart("file") MultipartFile file, @RequestParam ImportEntityType type) {
        return importService.upload(file, type);
    }

    @PostMapping("/{id}/validate")
    public ValidationResponse validate(@PathVariable UUID id, @Valid @RequestBody MappingRequest request) {
        return importService.validate(id, request.mapping());
    }

    @PostMapping("/{id}/confirm")
    public ImportReport confirm(@PathVariable UUID id, @RequestBody ConfirmRequest request) {
        return importService.confirm(id, request.skipInvalid());
    }

    @GetMapping("/{id}")
    public ImportReport get(@PathVariable UUID id) {
        return importService.get(id);
    }
}
