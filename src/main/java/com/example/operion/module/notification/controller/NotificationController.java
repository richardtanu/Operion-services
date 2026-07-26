package com.example.operion.module.notification.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.*;

import com.example.operion.common.response.ApiResponse;
import com.example.operion.module.notification.dto.NotificationResponse;
import com.example.operion.module.notification.dto.UnreadCountResponse;
import com.example.operion.module.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public ApiResponse<List<NotificationResponse>> getMyNotifications() {

        return ApiResponse.<List<NotificationResponse>>builder()
                .success(true)
                .message("Notifications fetched")
                .data(service.getMyNotifications())
                .build();
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> getUnreadCount() {

        return ApiResponse.<UnreadCountResponse>builder()
                .success(true)
                .message("Unread count fetched")
                .data(UnreadCountResponse.builder()
                        .unreadCount(service.getUnreadCount())
                        .build())
                .build();
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markRead(@PathVariable UUID id) {

        return ApiResponse.<NotificationResponse>builder()
                .success(true)
                .message("Notification marked as read")
                .data(service.markRead(id))
                .build();
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllRead() {

        service.markAllRead();

        return ApiResponse.<Void>builder()
                .success(true)
                .message("All notifications marked as read")
                .build();
    }
}
