package dev.adamgrochulski.javamon.app.auth;

import dev.adamgrochulski.javamon.app.auth.AuthDtos.AuthResponse;
import dev.adamgrochulski.javamon.app.auth.AuthDtos.LoginRequest;
import dev.adamgrochulski.javamon.app.auth.AuthDtos.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request.username(), request.password());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    /** Wejście bez rejestracji — token krótkożyjący, bez możliwości zapisu drużyn. */
    @PostMapping("/guest")
    public AuthResponse guest() {
        return authService.guest();
    }
}
