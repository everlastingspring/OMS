package com.oms.user.controller;

import com.oms.common.dto.ApiResponse;
import com.oms.common.dto.PageResponse;
import com.oms.common.security.UserPrincipal;
import com.oms.user.dto.AddressRequest;
import com.oms.user.dto.AddressResponse;
import com.oms.user.dto.UpdateUserRequest;
import com.oms.user.dto.UserResponse;
import com.oms.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Users", description = "Profile and address management")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated caller's own profile")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(userService.getById(principal.getId())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == principal.id")
    @Operation(summary = "Get a user by id",
            description = "A user may read only their own record; ADMIN may read any.")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getById(id)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Search users (ADMIN only)",
            description = "Case-insensitive match on name or email. Paged and sortable.")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> search(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(userService.search(keyword, pageable)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == principal.id")
    @Operation(summary = "Update name and phone",
            description = "Email and role are deliberately immutable through this endpoint.")
    public ResponseEntity<ApiResponse<UserResponse>> update(@PathVariable Long id,
                                                            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.update(id, request), "Profile updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == principal.id")
    @Operation(summary = "Deactivate an account",
            description = "Soft delete. The row is kept so historic orders still resolve to a customer.")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        userService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/addresses")
    @PreAuthorize("hasRole('ADMIN') or #id == principal.id")
    @Operation(summary = "List a user's saved addresses")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> addresses(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getAddresses(id)));
    }

    @PostMapping("/{id}/addresses")
    @PreAuthorize("hasRole('ADMIN') or #id == principal.id")
    @Operation(summary = "Add a shipping address",
            description = "The first address saved becomes the default automatically.")
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(@PathVariable Long id,
                                                                   @Valid @RequestBody AddressRequest request) {
        AddressResponse created = userService.addAddress(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Address added"));
    }

    @DeleteMapping("/{id}/addresses/{addressId}")
    @PreAuthorize("hasRole('ADMIN') or #id == principal.id")
    @Operation(summary = "Delete a saved address")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long id, @PathVariable Long addressId) {
        userService.deleteAddress(id, addressId);
        return ResponseEntity.noContent().build();
    }
}
