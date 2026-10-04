package com.nexus.pdv.report.api;

import com.nexus.pdv.audit.application.AuditService;
import com.nexus.pdv.audit.domain.AuditAction;
import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.plan.domain.FeatureCode;
import com.nexus.pdv.report.application.CsvReportWriter;
import com.nexus.pdv.report.application.PdfReportWriter;
import com.nexus.pdv.report.application.ReportService;
import com.nexus.pdv.report.domain.ReportResult;
import com.nexus.pdv.report.domain.ReportType;
import com.nexus.pdv.shared.access.AccessContext;
import com.nexus.pdv.shared.access.AccessContextService;
import com.nexus.pdv.shared.access.RequiresPermission;
import com.nexus.pdv.shared.time.Period;
import com.nexus.pdv.tenant.application.TenantSettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Relatórios")
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    public enum ExportFormat { CSV, PDF }

    private final ReportService reportService;
    private final TenantSettingsService settingsService;
    private final AccessContextService accessContextService;
    private final AuditService auditService;
    private final Clock clock;

    public ReportController(ReportService reportService, TenantSettingsService settingsService,
            AccessContextService accessContextService, AuditService auditService, Clock clock) {
        this.reportService = reportService;
        this.settingsService = settingsService;
        this.accessContextService = accessContextService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** Relatórios disponíveis e se o tenant/usuário pode acessá-los (para a tela e UX de upgrade). */
    @GetMapping
    @RequiresPermission(value = {Permission.REPORT_BASIC_READ, Permission.REPORT_ADVANCED_READ}, any = true)
    public List<ReportInfo> catalog() {
        AccessContext context = accessContextService.current();
        return Arrays.stream(ReportType.values())
                .map(type -> {
                    boolean featureOk = (type.permission().feature() == null || context.hasFeature(type.permission().feature()))
                            && (type.extraFeature() == null || context.hasFeature(type.extraFeature()));
                    boolean allowed = featureOk && context.hasPermission(type.permission())
                            && (type != ReportType.FINANCIAL || context.hasPermission(Permission.FINANCIAL_READ));
                    return new ReportInfo(type.name(), type.title(), type.advanced(), featureOk, allowed);
                })
                .toList();
    }

    @GetMapping("/{type}")
    @RequiresPermission(value = {Permission.REPORT_BASIC_READ, Permission.REPORT_ADVANCED_READ}, any = true)
    public ReportResult generate(@PathVariable ReportType type,
            @RequestParam(required = false) Period.Preset period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.generate(type, Period.resolve(period, from, to, settingsService.zone(), clock));
    }

    @GetMapping("/{type}/export")
    @RequiresPermission(Permission.REPORT_EXPORT)
    public ResponseEntity<byte[]> export(@PathVariable ReportType type, @RequestParam ExportFormat format,
            @RequestParam(required = false) Period.Preset period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        AccessContext context = accessContextService.current();
        context.requireFeature(format == ExportFormat.CSV ? FeatureCode.CSV_EXPORT : FeatureCode.PDF_EXPORT);
        var zone = settingsService.zone();
        ReportResult report = reportService.generate(type, Period.resolve(period, from, to, zone, clock));

        byte[] body;
        MediaType mediaType;
        String extension;
        if (format == ExportFormat.CSV) {
            body = CsvReportWriter.write(report, zone);
            mediaType = new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);
            extension = "csv";
        } else {
            body = PdfReportWriter.write(report, settingsService.get().companyName(), zone);
            mediaType = MediaType.APPLICATION_PDF;
            extension = "pdf";
        }
        auditService.record(AuditAction.EXPORT, "Report", type.name(),
                Map.of("format", format.name(), "from", report.from().toString(), "to", report.to().toString(),
                        "rows", report.rows().size()));
        String fileName = "relatorio-" + type.name().toLowerCase().replace('_', '-') + "-" + report.from() + "." + extension;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(body);
    }

    public record ReportInfo(String type, String title, boolean advanced, boolean availableInPlan, boolean allowed) {
    }
}
