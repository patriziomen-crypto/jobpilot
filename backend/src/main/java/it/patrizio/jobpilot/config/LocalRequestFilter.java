package it.patrizio.jobpilot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Due protezioni per un'app che gira in locale senza login:
 * 1. accetta solo richieste con Host 127.0.0.1 o localhost (contro il "DNS rebinding", con cui un sito
 *    esterno farebbe passare le proprie richieste come se fossero della stessa origine);
 * 2. per le richieste che modificano dati (POST/PUT/PATCH/DELETE) esige l'header X-Requested-With:
 *    il browser non lo manda mai da solo, quindi un sito esterno non può inviarle "alla cieca" (CSRF).
 */
@Component
public class LocalRequestFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    public static final String REQUIRED_HEADER = "X-Requested-With";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String host = request.getHeader("Host");
        String hostName = host == null ? "" : host.replaceFirst(":\\d+$", "").toLowerCase();
        if (!hostName.equals("127.0.0.1") && !hostName.equals("localhost") && !hostName.equals("[::1]")) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Host non ammesso");
            return;
        }
        if (request.getRequestURI().startsWith("/api/") && !SAFE_METHODS.contains(request.getMethod())
                && request.getHeader(REQUIRED_HEADER) == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Richiesta rifiutata: manca l'header X-Requested-With\"}");
            return;
        }
        // Le pagine dell'app (index.html e rotte Angular) non vanno mai messe in cache dal browser:
        // dopo un aggiornamento dell'app si deve caricare subito la versione nuova
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/") && !uri.matches(".*\\.[a-zA-Z0-9]{2,5}$") || uri.endsWith("/index.html")) {
            response.setHeader("Cache-Control", "no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
        }
        chain.doFilter(request, response);
    }
}
