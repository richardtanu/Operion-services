package com.example.operion.module.auth.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.tenant.entity.Tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends BaseEntity {

  @Id
  @GeneratedValue
  private UUID id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "full_name")
  private String fullName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  @Enumerated(EnumType.STRING)
  private Scope scope;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  /**
   * BE-33: false = switched off (left the business). Can't sign in, their
   * tokens stop working, not counted as one of the store's Owners, gets no
   * new notifications, can't be named on a new record. History keeps them.
   */
  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "deactivated_at")
  private LocalDateTime deactivatedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "deactivated_by")
  private User deactivatedBy;

  @Column(name = "deactivation_reason", length = 500)
  private String deactivationReason;

  /** BE-36: someone else chose the current password (a reset, or a new user). */
  @Column(name = "must_change_password", nullable = false)
  private boolean mustChangePassword = false;

  /** BE-36: last change or reset through the app, whole seconds; older tokens are refused. */
  @Column(name = "password_changed_at")
  private LocalDateTime passwordChangedAt;

  /**
   * BE-36: the one place a password is set through the app (change, reset,
   * register). Older tokens stop working; {@code chosenByThemself} false
   * means the person must change it at their next sign-in.
   */
  public void setNewPassword(String hash, boolean chosenByThemself) {

    this.passwordHash = hash;
    this.mustChangePassword = !chosenByThemself;
    this.passwordChangedAt = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
  }

  /** BE-33: for anyone named on a new record (requester, technician, taker). 400 when switched off. */
  public User requireActive() {

    if (!active) {
      throw new RuntimeException((fullName != null ? fullName : email) + " is switched off");
    }
    return this;
  }
}
