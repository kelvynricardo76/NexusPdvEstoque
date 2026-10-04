package com.nexus.pdv.auth.api;

import com.nexus.pdv.auth.api.AuthDtos.ChangePasswordRequest;
import com.nexus.pdv.auth.api.AuthDtos.LoginRequest;
import com.nexus.pdv.auth.api.AuthDtos.PlatformMeResponse;
import com.nexus.pdv.auth.application.AuthService;
import com.nexus.pdv.shared.security.AuthenticatedUser;
import com.nexus.pdv.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Super Admin — Autenticação")
@RestController
@RequestMapping("/api/super-admin/auth")
public class SuperAdminAuthController {

    private final AuthService authService;

    public SuperAdminAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public PlatformMeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        return toResponse(authService.loginPlatformAdmin(body.email(), body.password(), request, response));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        authService.logout(request);
    }

    @GetMapping("/me")
    public PlatformMeResponse me() {
        return toResponse(CurrentUser.require());
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest body) {
        authService.changeOwnPassword(body.currentPassword(), body.newPassword());
    }

    private static PlatformMeResponse toResponse(AuthenticatedUser user) {
        return new PlatformMeResponse(user.type().name(), user.id(), user.name(), user.email());
    }
}
