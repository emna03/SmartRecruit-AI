package com.smartrecruit.user.controller;

import com.smartrecruit.security.AppUserDetails;
import com.smartrecruit.user.dto.UpdateUserStatusRequest;
import com.smartrecruit.user.dto.UserResponse;
import com.smartrecruit.user.entity.RoleName;
import com.smartrecruit.user.service.UserAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Gestion des utilisateurs par l'administrateur (accès ADMIN imposé par SecurityConfig sur /api/admin/**). */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserAdminService userAdminService;

    @GetMapping
    public List<UserResponse> list(@RequestParam(required = false) RoleName role) {
        return userAdminService.findAll(role);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return userAdminService.findById(id);
    }

    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody UpdateUserStatusRequest request,
                                     @AuthenticationPrincipal AppUserDetails admin) {
        return userAdminService.updateStatus(id, request.enabled(), admin.getId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails admin) {
        userAdminService.delete(id, admin.getId());
    }
}
