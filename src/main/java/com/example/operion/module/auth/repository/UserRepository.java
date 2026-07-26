package com.example.operion.module.auth.repository;

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

    @Query("select u from User u where u.tenant.id in :tenantIds and (u.role = :role or u.scope in :scopes)")
    List<User> findByTenantIdInAndRoleOrScopeIn(
            @Param("tenantIds") List<UUID> tenantIds,
            @Param("role") Role role,
            @Param("scopes") List<Scope> scopes);
}

