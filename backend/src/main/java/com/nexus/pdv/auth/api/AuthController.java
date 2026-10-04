package com.nexus.pdv.auth.api;

import com.nexus.pdv.auth.api.AuthDtos.ChangePasswordRequest;
import com.nexus.pdv.auth.api.AuthDtos.ForgotPasswordRequest;
import com.nexus.pdv.auth.api.AuthDtos.LoginRequest;
import com.nexus.pdv.auth.api.AuthDtos.ResetPasswordRequest;
import com.nexus.pdv.auth.api.AuthDtos.TenantMeResponse;
import com.nexus.pdv.auth.application.AuthService;
import com.nexus.pdv.auth.application.MeService;
import com.nexus.pdv.auth.application.PasswordResetService;
import com.nexus.pdv.shared.error.BusinessException;
import com.nexus.pdv.shared.error.ErrorCode;
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

@Tag(name = "Autenticação")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final MeService meService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, MeService meService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.meService = meService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/login")
    public TenantMeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        authService.loginTenantUser(body.email(), body.password(), request, response);
        return meService.tenantMe();
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        authService.logout(request);
    }

    @GetMapping("/me")
    public TenantMeResponse me() {
        if (CurrentUser.require().isPlatformAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return meService.tenantMe();
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest body) {
        passwordResetService.requestReset(body.email());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest body) {
        passwordResetService.resetPassword(body.token(), body.newPassword());
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest body) {
        authService.changeOwnPassword(body.currentPassword(), body.newPassword());
    }
}
