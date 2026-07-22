package dev.adamgrochulski.javamon.app.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Czyta Bearer token i — jeśli jest ważny — wstawia tożsamość do SecurityContext.
 * Świadomie NIE jest beanem Springa: bean typu Filter zostałby przez Boota
 * zarejestrowany dodatkowo w łańcuchu serwletowym i uruchamiałby się dwa razy,
 * także dla ścieżek spoza Spring Security. Tworzy go SecurityConfig.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";
    private final JwtService jwtService;

    JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(PREFIX)) {
            jwtService.verify(header.substring(PREFIX.length()))
                    .ifPresent(JwtAuthenticationFilter::authenticate);
        }
        // Zły token traktujemy jak jego brak: kontekst zostaje pusty, a decyzję
        // podejmuje warstwa autoryzacji. Filtr nigdy nie kończy tu żądania sam.
        chain.doFilter(request, response);
    }

    private static void authenticate(AuthenticatedTrainer trainer) {
        var authority = new SimpleGrantedAuthority(trainer.guest() ? "ROLE_GUEST" : "ROLE_USER");
        var authentication = new UsernamePasswordAuthenticationToken(
                trainer, null, List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
