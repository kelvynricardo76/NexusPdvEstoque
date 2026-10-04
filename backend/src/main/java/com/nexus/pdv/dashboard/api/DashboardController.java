package com.nexus.pdv.dashboard.api;

import com.nexus.pdv.dashboard.api.DashboardDtos.DashboardResponse;
import com.nexus.pdv.dashboard.api.DashboardDtos.NotificationItem;
import com.nexus.pdv.dashboard.api.DashboardDtos.SearchResponse;
import com.nexus.pdv.dashboard.application.DashboardService;
import com.nexus.pdv.dashboard.application.WorkspaceService;
import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.shared.access.RequiresPermission;
import com.nexus.pdv.shared.access.TenantAuthenticated;
import com.nexus.pdv.shared.time.Period;
import com.nexus.pdv.tenant.application.TenantSettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard")
@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboardService;
    private final WorkspaceService workspaceService;
    private final TenantSettingsService settingsService;
    private final Clock clock;

    public DashboardController(DashboardService dashboardService, WorkspaceService workspaceService,
            TenantSettingsService settingsService, Clock clock) {
        this.dashboardService = dashboardService;
        this.workspaceService = workspaceService;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @GetMapping("/dashboard")
    @RequiresPermission(Permission.DASHBOARD_VIEW)
    public DashboardResponse dashboard(@RequestParam(required = false) Period.Preset period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Period.Preset preset = period == null && from == null && to == null ? Period.Preset.TODAY : period;
        return dashboardService.dashboard(Period.resolve(preset, from, to, settingsService.zone(), clock));
    }

    @GetMapping("/notifications")
    @TenantAuthenticated
    public List<NotificationItem> notifications() {
        return workspaceService.notifications();
    }

    @GetMapping("/search")
    @TenantAuthenticated
    public SearchResponse search(@RequestParam String q) {
        return workspaceService.search(q);
    }
}
