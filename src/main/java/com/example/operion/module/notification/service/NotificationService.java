package com.example.operion.module.notification.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.operion.module.airsoft.entity.AirsoftUnit;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.notification.dto.NotificationResponse;
import com.example.operion.module.notification.entity.Notification;
import com.example.operion.module.notification.enums.NotificationReferenceType;
import com.example.operion.module.notification.enums.NotificationType;
import com.example.operion.module.notification.repository.NotificationRepository;
import com.example.operion.module.part.entity.Part;
import com.example.operion.module.procurement.entity.PurchaseRequest;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.service.TenantHierarchyService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final List<Scope> OWNER_OR_ABOVE = Arrays.stream(Scope.values())
            .filter(s -> s.ordinal() >= Scope.OWNER.ordinal())
            .toList();

    private final NotificationRepository repository;

    private final UserRepository userRepository;

    private final TenantHierarchyService tenantHierarchyService;

    public void notifyNewPurchaseRequest(PurchaseRequest pr) {

        List<UUID> tenantIds = tenantHierarchyService.getAncestorTenantIds(pr.getTenant().getId());

        String requesterName = pr.getRequestedBy() != null ? pr.getRequestedBy().getFullName() : "Unknown";

        for (User target : approversIn(tenantIds)) {

            create(
                    pr.getTenant(),
                    target,
                    NotificationType.NEW_PURCHASE_REQUEST,
                    "New Purchase Request",
                    "New purchase request submitted by " + requesterName + " at " + pr.getTenant().getName(),
                    NotificationReferenceType.PURCHASE_REQUEST,
                    pr.getId());
        }
    }

    public void notifyPurchaseRequestOrdered(PurchaseRequest pr) {

        if (pr.getRequestedBy() == null) {
            return;
        }

        create(
                pr.getTenant(),
                pr.getRequestedBy(),
                NotificationType.PURCHASE_REQUEST_ORDERED,
                "Purchase Request Ordered",
                "Your purchase request has been ordered from the supplier",
                NotificationReferenceType.PURCHASE_REQUEST,
                pr.getId());
    }

    public void notifyPartEndOfLife(AirsoftUnit unit, String message) {

        for (User target : approversIn(List.of(unit.getTenant().getId()))) {

            create(
                    unit.getTenant(),
                    target,
                    NotificationType.PART_END_OF_LIFE,
                    "Sparepart Nearing End Of Life",
                    unit.getName() + ": " + message,
                    NotificationReferenceType.AIRSOFT_UNIT,
                    unit.getId());
        }
    }

    public void notifyLowStock(Part part) {

        for (User target : approversIn(List.of(part.getTenant().getId()))) {

            create(
                    part.getTenant(),
                    target,
                    NotificationType.LOW_STOCK,
                    "Low Stock",
                    part.getName() + " has dropped to or below its minimum stock level",
                    NotificationReferenceType.PART,
                    part.getId());
        }
    }

    public List<NotificationResponse> getMyNotifications() {

        return repository.findByRecipientUserId(currentUserId())
                .stream()
                .map(this::map)
                .toList();
    }

    public long getUnreadCount() {

        return repository.countUnreadByRecipientUserId(currentUserId());
    }

    @Transactional
    public NotificationResponse markRead(UUID id) {

        Notification notification = repository
                .findByIdAndRecipientUserId(id, currentUserId())
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        notification.setRead(true);
        notification.setReadAt(LocalDateTime.now());

        return map(repository.save(notification));
    }

    @Transactional
    public void markAllRead() {

        List<Notification> unread = repository.findUnreadByRecipientUserId(currentUserId());

        LocalDateTime now = LocalDateTime.now();

        for (Notification notification : unread) {
            notification.setRead(true);
            notification.setReadAt(now);
        }

        repository.saveAll(unread);
    }

    private List<User> approversIn(List<UUID> tenantIds) {

        return userRepository.findByTenantIdInAndRoleOrScopeIn(tenantIds, Role.ADMIN, OWNER_OR_ABOVE);
    }

    private void create(
            Tenant tenant,
            User recipient,
            NotificationType type,
            String title,
            String message,
            NotificationReferenceType referenceType,
            UUID referenceId) {

        repository.save(
                Notification.builder()
                        .tenant(tenant)
                        .recipientUser(recipient)
                        .type(type)
                        .title(title)
                        .message(message)
                        .referenceType(referenceType)
                        .referenceId(referenceId)
                        .read(false)
                        .build());
    }

    private UUID currentUserId() {

        return UUID.fromString(
                SecurityContextHolder.getContext().getAuthentication().getName());
    }

    private NotificationResponse map(Notification notification) {

        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType().name())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .referenceType(
                        notification.getReferenceType() != null ? notification.getReferenceType().name() : null)
                .referenceId(notification.getReferenceId())
                .read(notification.isRead())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
