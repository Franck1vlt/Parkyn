package app.parkyn.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import app.parkyn.IntegrationTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@AutoConfigureMockMvc 
@IntegrationTest 
public class JwtAuthFilterTest {

    @Autowired MockMvc mockMvc;

    // --- Tests ---

    @Test 
    public void malformedTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer not-a-valid-jwt"))
            .andExpect(status().isUnauthorized());
    }

    @Test 
    public void tokenSignedWithAnotherKeyIsUnauthorized() throws Exception {
        String forged = Jwts.builder()
            .subject("test@parkyn.app")
            .signWith(Keys.hmacShaKeyFor(new byte[32]))
            .compact();

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + forged))
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void emptyBearerTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer "))
            .andExpect(status().isUnauthorized());
    }

}
