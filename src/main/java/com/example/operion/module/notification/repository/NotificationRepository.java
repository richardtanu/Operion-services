package com.example.operion.module.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.operion.module.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("select n from Notification n where n.recipientUser.id = :userId order by n.createdAt desc")
    List<Notification> findByRecipientUserId(@Param("userId") UUID userId);

    @Query("select count(n) from Notification n where n.recipientUser.id = :userId and n.read = false")
    long countUnreadByRecipientUserId(@Param("userId") UUID userId);

    @Query("select n from Notification n where n.id = :id and n.recipientUser.id = :userId")
    Optional<Notification> findByIdAndRecipientUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("select n from Notification n where n.recipientUser.id = :userId and n.read = false")
    List<Notification> findUnreadByRecipientUserId(@Param("userId") UUID userId);
}
