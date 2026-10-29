package com.example.operion.module.upload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.enums.Role;
import com.example.operion.module.auth.enums.Scope;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.auth.security.JwtUtil;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.upload.repository.UploadRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * BE-30 over a real server (Tomcat on a random port), the way the phone sends
 * it: the raw file body to the returned link, with only the returned headers.
 * Also proves the links carry the address and port the phone used. Not
 * transactional (the server runs on its own threads), so it cleans up after itself.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { "uploads.storage=local", "uploads.local.dir=target/test-local-uploads" })
class LocalUploadHttpTest {

    @LocalServerPort private int port;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UploadRepository uploadRepository;

    private final HttpClient http = HttpClient.newHttpClient();

    private Tenant tenant;
    private User user;

    @AfterEach
    void cleanUp() {

        if (user != null) {
            uploadRepository.findAll().stream()
                    .filter(u -> u.getUploadedBy().getId().equals(user.getId()))
                    .forEach(uploadRepository::delete);
            userRepository.delete(user);
        }
        if (tenant != null) {
            tenantRepository.delete(tenant);
        }
    }

    @Test
    void realServer_putThenGet() throws Exception {

        String s = UUID.randomUUID().toString().substring(0, 8);
        tenant = tenantRepository.save(Tenant.builder().code("BE30H_" + s).name("Store").active(true).build());
        user = new User();
        user.setEmail("be30h-" + s + "@test.com");
        user.setPasswordHash("not-a-real-hash");
        user.setFullName("be30h-" + s);
        user.setRole(Role.OPERATOR);
        user.setScope(Scope.MANAGER);
        user.setTenant(tenant);
        user = userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId().toString(), "OPERATOR", tenant.getId().toString(), "MANAGER");
        String base = "http://localhost:" + port;

        byte[] photo = new byte[123_457];
        new Random(7).nextBytes(photo);

        HttpResponse<String> ticket = http.send(HttpRequest.newBuilder(URI.create(base + "/uploads"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"purpose\":\"GOODS_RECEIPT_PHOTO\",\"contentType\":\"image/jpeg\","
                        + "\"sizeBytes\":" + photo.length + "}"))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, ticket.statusCode(), ticket.body());
        String putUrl = JsonPath.read(ticket.body(), "$.data.putUrl");
        String uploadId = JsonPath.read(ticket.body(), "$.data.uploadId");
        assertTrue(putUrl.startsWith(base + "/uploads-local?"), "the address the phone used: " + putUrl);

        HttpResponse<String> put = http.send(HttpRequest.newBuilder(URI.create(putUrl))
                .header("Content-Type", "image/jpeg")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(photo))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, put.statusCode(), put.body());

        HttpResponse<String> view = http.send(HttpRequest.newBuilder(URI.create(base + "/uploads/" + uploadId))
                .header("Authorization", "Bearer " + token)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        String getUrl = JsonPath.read(view.body(), "$.data.getUrl");

        HttpResponse<byte[]> file = http.send(HttpRequest.newBuilder(URI.create(getUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, file.statusCode());
        assertEquals("image/jpeg", file.headers().firstValue("Content-Type").orElse(""));
        assertArrayEquals(photo, file.body());
    }
}
