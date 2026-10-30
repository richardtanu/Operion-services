package com.example.operion.module.auth.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    List<User> findByTenantIdAndRole(UUID tenantId, Role role);

    /** BE-25: a store's users with one of these scopes (its own Owner+, not HQ's). */
    List<User> findByTenantIdAndScopeIn(UUID tenantId, Collection<Scope> scopes);

    /** BE-33: the same, switched-off users left out (who counts as the store's Owners). */
    List<User> findByTenantIdAndScopeInAndActiveTrue(UUID tenantId, Collection<Scope> scopes);

    /** BE-33: {@code GET /users}. */
    @Query("select u from User u where u.tenant.id in :tenantIds and (:active is null or u.active = :active)"
            + " order by u.fullName")
    List<User> findInTenants(@Param("tenantIds") List<UUID> tenantIds, @Param("active") Boolean active);

    @Query("select u from User u where u.tenant.id in :tenantIds and (u.role = :role or u.scope in :scopes)")
    List<User> findByTenantIdInAndRoleOrScopeIn(
            @Param("tenantIds") List<UUID> tenantIds,
            @Param("role") Role role,
            @Param("scopes") List<Scope> scopes);
}

