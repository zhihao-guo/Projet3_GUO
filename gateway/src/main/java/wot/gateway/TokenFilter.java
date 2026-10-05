package wot.gateway;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Bearer token on /things/** and /events/** (token: gateway.token). Dashboard files are public.
 * TODO (bonus): roles viewer / operator, 403.
 */
@Component
public class TokenFilter extends OncePerRequestFilter {

    private final String operatorToken;
    private final String viewerToken;

    public TokenFilter(@Value("${gateway.token}") String operatorToken,
            @Value("${gateway.viewer-token:viewer-secret}") String viewerToken) {
        if (operatorToken.isBlank() || viewerToken.isBlank() || operatorToken.equals(viewerToken)) {
            throw new IllegalArgumentException("operator and viewer tokens must be non-empty and different");
        }
        this.operatorToken = operatorToken;
        this.viewerToken = viewerToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals("/things") || path.startsWith("/things/")
                || path.equals("/events") || path.startsWith("/events/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = null;
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring("Bearer ".length()).trim();
        } else if (request.getRequestURI().equals("/events/stream")) {
            // EventSource cannot send headers
            token = request.getParameter("token");
        }

        String role;
        if (operatorToken.equals(token)) role = "operator";
        else if (viewerToken.equals(token)) role = "viewer";
        else {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            reject(response, 401, "missing or invalid token");
            return;
        }
        response.setHeader("X-Access-Role", role);
        request.setAttribute("accessRole", role);
        boolean read = request.getMethod().equals("GET") || request.getMethod().equals("HEAD");
        if (role.equals("viewer") && !read) {
            reject(response, 403, "viewer is read-only; operator token required");
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":" + status + ",\"error\":\"" + message + "\"}");
    }
}
