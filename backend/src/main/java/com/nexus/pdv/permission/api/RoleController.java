package com.nexus.pdv.permission.api;

import com.nexus.pdv.permission.application.RoleService;
import com.nexus.pdv.permission.domain.Permission;
import com.nexus.pdv.shared.access.RequiresPermission;
import com.nexus.pdv.user.api.UserDtos.PermissionResponse;
import com.nexus.pdv.user.api.UserDtos.RoleRequest;
import com.nexus.pdv.user.api.UserDtos.RoleResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cargos e permissões")
@RestController
@RequestMapping("/api")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/roles")
    @RequiresPermission(Permission.USER_READ)
    public List<RoleResponse> list() {
        return roleService.list();
    }

    @GetMapping("/roles/{id}")
    @RequiresPermission(Permission.USER_READ)
    public RoleResponse get(@PathVariable UUID id) {
        return roleService.get(id);
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.USER_PERMISSION_MANAGE)
    public RoleResponse create(@Valid @RequestBody RoleRequest request) {
        return roleService.create(request);
    }

    @PutMapping("/roles/{id}")
    @RequiresPermission(Permission.USER_PERMISSION_MANAGE)
    public RoleResponse update(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        return roleService.update(id, request);
    }

    @DeleteMapping("/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Permission.USER_PERMISSION_MANAGE)
    public void delete(@PathVariable UUID id) {
        roleService.delete(id);
    }

    @GetMapping("/permissions")
    @RequiresPermission(Permission.USER_READ)
    public List<PermissionResponse> permissions() {
        return roleService.catalog();
    }
}
