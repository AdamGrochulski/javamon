package dev.adamgrochulski.javamon.app.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank
            @Size(min = 3, max = 32)
            @Pattern(regexp = "[A-Za-z0-9_-]+",
                    message = "dozwolone są litery, cyfry, podkreślnik i myślnik")
            String username,

            // Górna granica nie jest kaprysem: BCrypt bierze pod uwagę tylko
            // pierwsze 72 bajty i resztę po cichu ignoruje.
            @NotBlank
            @Size(min = 8, max = 72)
            String password) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record AuthResponse(String token, String username, boolean guest) {
    }
}
