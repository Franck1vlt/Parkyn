package app.parkyn.tenant;

import java.util.UUID;

public final class Tenants {

    private Tenants() {
        // classe utilitaire : uniquement des constantes, jamais instanciée
    }

    // Tenant par défaut (V6) : celui des inscriptions, en attendant les invitations du lot 4
    public static final UUID DEFAULT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
}
