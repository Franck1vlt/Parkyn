package app.parkyn.service;

// Origine d'une requête d'authentification, lue par le controller (IP via ClientIpResolver)
public record ClientInfo(String userAgent, String ip) {
}
