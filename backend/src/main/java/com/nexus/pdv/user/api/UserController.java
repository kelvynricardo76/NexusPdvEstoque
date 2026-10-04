package com.nexus.pdv.user.api;

import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.shared.access.RequiresPermission;
import com.nexus.pdv.shared.web.PageResponse;
import com.nexus.pdv.user.api.UserDtos.CreateUserRequest;
import com.nexus.pdv.user.api.UserDtos.PermissionOverridesRequest;
import com.nexus.pdv.user.api.UserDtos.UpdateUserRequest;
import com.nexus.pdv.user.api.UserDtos.UserDetailResponse;
import com.nexus.pdv.user.api.UserDtos.UserResponse;
import com.nexus.pdv.user.api.UserDtos.UserStatusRequest;
import com.nexus.pdv.user.application.UserService;
import com.nexus.pdv.user.domain.UserStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Usuários")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @RequiresPermission(Permission.USER_READ)
    public PageResponse<UserResponse> search(@RequestParam(required = false) String q,
            @RequestParam(required = false) UserStatus status,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(userService.search(q, status, pageable));
    }

    @GetMapping("/{id}")
    @RequiresPermission(Permission.USER_READ)
    public UserDetailResponse detail(@PathVariable UUID id) {
        return userService.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.USER_CREATE)
    public UserDetailResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    @RequiresPermission(Permission.USER_UPDATE)
    public UserDetailResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @PutMapping("/{id}/status")
    @RequiresPermission(Permission.USER_DISABLE)
    public UserDetailResponse changeStatus(@PathVariable UUID id, @RequestBody UserStatusRequest request) {
        return userService.changeStatus(id, request.active());
    }

    @PutMapping("/{id}/permissions")
    @RequiresPermission(Permission.USER_PERMISSION_MANAGE)
    public UserDetailResponse replaceOverrides(@PathVariable UUID id,
            @Valid @RequestBody PermissionOverridesRequest request) {
        return userService.replaceOverrides(id, request.overrides());
    }

    @PostMapping("/{id}/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Permission.USER_UPDATE)
    public void requestPasswordReset(@PathVariable UUID id) {
        userService.requestPasswordReset(id);
    }
}
