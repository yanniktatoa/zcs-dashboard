package com.zerocoldstart.dashboard.controller;

import com.zerocoldstart.dashboard.model.ExportRequest;
import com.zerocoldstart.dashboard.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @PostMapping("/pdf")
    public ResponseEntity<byte[]> exportPdf(@RequestBody ExportRequest request) throws Exception {
        byte[] pdf = exportService.exportPdf(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=benchmark-report.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/latex")
    public ResponseEntity<String> exportLatex(@RequestBody ExportRequest request) {
        String latex = exportService.exportLatex(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=benchmark-report.tex")
                .contentType(MediaType.TEXT_PLAIN)
                .body(latex);
    }
}
