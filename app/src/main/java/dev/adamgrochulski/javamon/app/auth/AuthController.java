package dev.adamgrochulski.javamon.app.auth;

import dev.adamgrochulski.javamon.app.auth.AuthDtos.AuthResponse;
import dev.adamgrochulski.javamon.app.auth.AuthDtos.LoginRequest;
import dev.adamgrochulski.javamon.app.auth.AuthDtos.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
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
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request.username(), request.password(), ipOf(http));
    }

    /**
     * Wejście bez rejestracji. Token krótkożyjący; konto jest pełnoprawne
     * (gość musi móc zbudować drużynę, żeby w ogóle wejść do walki), ale
     * podlega sprzątaniu — patrz docs/security.md, pozycja B2.
     */
    @PostMapping("/guest")
    public AuthResponse guest(HttpServletRequest http) {
        return authService.guest(ipOf(http));
    }

    /**
     * Prawdziwy adres klienta, nie proxy: rozwija go Spring dzięki
     * forward-headers-strategy=framework. Bez tego limity objęłyby wszystkich naraz.
     */
    private static String ipOf(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
