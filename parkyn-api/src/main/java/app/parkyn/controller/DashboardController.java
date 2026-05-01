package app.parkyn.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.parkyn.dto.DashboardResponse;
import app.parkyn.model.User;
import app.parkyn.service.DashboardService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    @GetMapping("")
    public ResponseEntity<DashboardResponse> getDashboard(@AuthenticationPrincipal User user){
        return ResponseEntity.ok(dashboardService.getDashboard(user));
    }
}

