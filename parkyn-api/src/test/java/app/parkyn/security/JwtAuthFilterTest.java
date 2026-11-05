package app.parkyn.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import app.parkyn.IntegrationTest;
import app.parkyn.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@AutoConfigureMockMvc 
@IntegrationTest 
public class JwtAuthFilterTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;

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

    // Token bien signé et non expiré, mais l'email ne correspond plus à aucun compte
    // (compte supprimé en base, ou email modifié par un admin après l'émission du token)
    @Test
    public void validTokenOfUnknownUserIsUnauthorized() throws Exception {
        User unknown = new User();
        unknown.setEmail("inconnu@parkyn.app");

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + jwtService.generateToken(unknown)))
            .andExpect(status().isUnauthorized());
    }

}
