package com.example.operion.module.upload.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * BE-30: uploads kept on this machine's disk, for running without an R2
 * bucket (local development and testing). Same contract as {@link R2Storage}:
 * the phone gets a signed PUT URL and sends the bytes there with no login; it
 * just points back at this server ({@code /uploads-local}) instead of R2.
 *
 * A link is an HMAC signature over what it allows (method, key, and for PUT
 * the declared type and size) plus its expiry, the way R2's presigned links
 * work, so it can't be reused for another file, a bigger file, or after it
 * expires. The signing key is random per start unless
 * {@code uploads.local.signing-key} is set, so a restart ends open links.
 *
 * Not for production: a deployed server's disk is wiped on every redeploy
 * (Railway), and every file passes through this server. Use {@code r2} there.
 */
@Component
@ConditionalOnProperty(name = "uploads.storage", havingValue = "local", matchIfMissing = true)
public class LocalDiskStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalDiskStorage.class);

    static final String PATH = "/uploads-local";

    private static final String TYPE_SUFFIX = ".content-type";

    private final Path root;

    private final String publicBaseUrl;

    private final byte[] signingKey;

    public LocalDiskStorage(
            @Value("${uploads.local.dir:local-uploads}") String dir,
            @Value("${uploads.local.public-base-url:}") String publicBaseUrl,
            @Value("${uploads.local.signing-key:}") String signingKey) {

        this.root = Path.of(dir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;

        if (signingKey.isBlank()) {
            this.signingKey = new byte[32];
            new SecureRandom().nextBytes(this.signingKey);
        } else {
            this.signingKey = signingKey.getBytes(StandardCharsets.UTF_8);
        }

        log.warn("Uploads are stored on this machine's disk at {} (UPLOAD_STORAGE=local). "
                + "Not for production: use UPLOAD_STORAGE=r2 there.", root);
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public void requireConfigured() {
        // always available
    }

    @Override
    public Presigned presignPut(String key, String contentType, long sizeBytes, Duration ttl) {

        Instant expires = Instant.now().plus(ttl);
        String sig = sign("PUT", key, contentType, String.valueOf(sizeBytes), String.valueOf(expires.getEpochSecond()));

        String url = UriComponentsBuilder.fromUriString(baseUrl() + PATH)
                .queryParam("key", key)
                .queryParam("ct", contentType)
                .queryParam("size", sizeBytes)
                .queryParam("expires", expires.getEpochSecond())
                .queryParam("sig", sig)
                .encode()
                .toUriString();

        return new Presigned(url, expires);
    }

    @Override
    public Presigned presignGet(String key, Duration ttl) {

        Instant expires = Instant.now().plus(ttl);
        String sig = sign("GET", key, String.valueOf(expires.getEpochSecond()));

        String url = UriComponentsBuilder.fromUriString(baseUrl() + PATH)
                .queryParam("key", key)
                .queryParam("expires", expires.getEpochSecond())
                .queryParam("sig", sig)
                .encode()
                .toUriString();

        return new Presigned(url, expires);
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    @Override
    public void delete(String key) {

        try {
            Path file = resolve(key);
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + TYPE_SUFFIX));
        } catch (IOException e) {
            throw new IllegalStateException("Could not delete upload " + key, e);
        }
    }

    /**
     * The PUT a signed link allows. The body must have the declared type and
     * at most the declared size (and not be empty), like R2 enforces.
     */
    public void store(String key, String contentType, long sizeBytes, long expires, String sig,
            String requestContentType, InputStream body) {

        requireValid(sig, expires, "PUT", key, contentType, String.valueOf(sizeBytes), String.valueOf(expires));

        if (requestContentType == null || !baseType(requestContentType).equals(baseType(contentType))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content-Type must be " + contentType);
        }

        Path file = resolve(key);

        try {
            Files.createDirectories(file.getParent());
            Path partial = Files.createTempFile(file.getParent(), "upload-", ".part");

            long written;
            try (OutputStream out = Files.newOutputStream(partial)) {
                written = copyAtMost(body, out, sizeBytes);
            }

            if (written < 0 || written == 0) {
                Files.deleteIfExists(partial);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        written == 0 ? "The file is empty" : "The file is larger than declared");
            }

            Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(file.resolveSibling(file.getFileName() + TYPE_SUFFIX), baseType(contentType));

        } catch (IOException e) {
            throw new IllegalStateException("Could not save upload " + key, e);
        }
    }

    /** The GET a signed link allows: the file and its content type. */
    public StoredFile read(String key, long expires, String sig) {

        requireValid(sig, expires, "GET", key, String.valueOf(expires));

        Path file = resolve(key);

        if (!Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload not found");
        }

        try {
            Path typeFile = file.resolveSibling(file.getFileName() + TYPE_SUFFIX);
            String type = Files.isRegularFile(typeFile) ? Files.readString(typeFile).trim() : "application/octet-stream";
            return new StoredFile(file, type);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read upload " + key, e);
        }
    }

    public record StoredFile(Path path, String contentType) {
    }

    // ---- internals ----

    private void requireValid(String sig, long expires, String... parts) {

        if (sig == null || Instant.now().getEpochSecond() > expires) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This upload link has expired");
        }

        byte[] expected = sign(parts).getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This upload link is not valid");
        }
    }

    private String sign(String... parts) {

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            byte[] digest = mac.doFinal(String.join("\n", parts).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    /** Keys are server-made ({tenant}/{purpose}/{id}); still, never leave the folder. */
    private Path resolve(String key) {

        Path file = root.resolve(key).normalize();

        if (!file.startsWith(root) || file.equals(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload not found");
        }

        return file;
    }

    /** The links point back at whatever address the phone used to reach this server. */
    private String baseUrl() {

        if (!publicBaseUrl.isBlank()) {
            return publicBaseUrl;
        }

        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        } catch (IllegalStateException noRequest) {
            return "http://localhost:8080";
        }
    }

    private static String baseType(String contentType) {

        int semicolon = contentType.indexOf(';');
        return (semicolon >= 0 ? contentType.substring(0, semicolon) : contentType).trim().toLowerCase(Locale.ROOT);
    }

    /** Copies up to {@code max} bytes; -1 if the body is longer than that. */
    private static long copyAtMost(InputStream in, OutputStream out, long max) throws IOException {

        byte[] buffer = new byte[8192];
        long total = 0;
        int n;

        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > max) {
                return -1;
            }
            out.write(buffer, 0, n);
        }

        return total;
    }
}
