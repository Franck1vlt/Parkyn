package app.parkyn.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

// IP du client : nginx la transmet dans X-Real-IP, l'en-tête n'est cru que s'il vient d'un proxy de confiance
@Component
public class ClientIpResolver {

    private final List<IpAddressMatcher> trustedProxyMatchers;

    public ClientIpResolver(@Value("${parkyn.rate-limit.trusted-proxies}") String trustedProxies) {
        trustedProxyMatchers = Arrays.stream(trustedProxies.split(","))
                .map(String::strip)
                .filter(proxy -> !proxy.isEmpty())
                .map(IpAddressMatcher::new)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        boolean trustedProxy = trustedProxyMatchers.stream()
                .anyMatch(matcher -> matcher.matches(remoteAddress));

        String xRealIp = request.getHeader("X-Real-IP");
        if (trustedProxy && xRealIp != null) {
            String cleanedXRealIp = xRealIp.strip();
            if (!cleanedXRealIp.isEmpty()) {
                return cleanedXRealIp;
            }
        }

        return remoteAddress;
    }

}
