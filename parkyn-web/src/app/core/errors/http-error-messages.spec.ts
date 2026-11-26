import { HttpErrorResponse } from '@angular/common/http';
import { HANDLED_STATUSES } from '../interceptors/error-handling';
import { describeHttpError, HttpAction, httpErrorContext } from './http-error-messages';

const NOT_FOUND_TEXT = "Ce véhicule n'existe plus.";
const DELETE_FORBIDDEN_TEXT = "Vous n'avez pas le droit de supprimer ce véhicule.";
const LOAD_FORBIDDEN_TEXT = "Vous n'avez pas accès à la liste des véhicules.";
const DELETE_CONFLICT_TEXT =
  'Ce véhicule a des réservations à venir ou en cours : annulez-les avant de le supprimer.';
const USERS_FORBIDDEN_TEXT = "Vous n'avez pas accès à la liste des utilisateurs.";

function httpError(status: number, body: unknown = null): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: body });
}

describe('describeHttpError', () => {
  it('describes a vanished vehicle on a 404 when deleting', () => {
    expect(describeHttpError(httpError(404), 'delete-vehicle')).toBe(NOT_FOUND_TEXT);
  });

  it('describes missing rights on a 403 when deleting, distinct from the 404 text', () => {
    const message = describeHttpError(httpError(403), 'delete-vehicle');

    expect(message).toBe(DELETE_FORBIDDEN_TEXT);
    expect(message).not.toBe(describeHttpError(httpError(404), 'delete-vehicle'));
  });

  // Le texte est fixe : le corps texte brut de l'API n'est jamais affiché
  it('describes active reservations on a 409 when deleting, distinct from the 403 and 404 texts', () => {
    const message = describeHttpError(
      httpError(409, 'Vehicle has active reservations'),
      'delete-vehicle',
    );

    expect(message).toBe(DELETE_CONFLICT_TEXT);
    expect(message).not.toBe(describeHttpError(httpError(403), 'delete-vehicle'));
    expect(message).not.toBe(describeHttpError(httpError(404), 'delete-vehicle'));
  });

  it('words a 403 differently for loading the list and for deleting', () => {
    const load = describeHttpError(httpError(403), 'load-vehicles');

    expect(load).toBe(LOAD_FORBIDDEN_TEXT);
    expect(load).not.toBe(describeHttpError(httpError(403), 'delete-vehicle'));
  });

  // Les autres statuts donnent le message générique, jamais le corps de l'API
  it.each<[HttpAction, number]>([
    ['delete-vehicle', 418],
    ['delete-vehicle', 500],
    ['load-vehicles', 404],
  ])(
    'returns exactly the generic message and never the API body for %s with status %i',
    (action, status) => {
      const message = describeHttpError(httpError(status, { message: 'SQL boom' }), action);

      expect(message).toBe('Une erreur est survenue');
      expect(message).not.toContain('SQL boom');
    },
  );
});

describe('describeHttpError for load-users', () => {
  // Texte propre à la liste des utilisateurs, distinct de celui des véhicules
  it('describes missing rights on a 403 when loading the users list', () => {
    const message = describeHttpError(httpError(403), 'load-users');

    expect(message).toBe(USERS_FORBIDDEN_TEXT);
    expect(message).not.toBe(describeHttpError(httpError(403), 'load-vehicles'));
  });

  // Les autres statuts donnent le message générique, jamais le corps de l'API
  it.each([404, 418, 500])(
    'returns exactly the generic message and never the API body for load-users with status %i',
    (status) => {
      const message = describeHttpError(httpError(status, { message: 'SQL boom' }), 'load-users');

      expect(message).toBe('Une erreur est survenue');
      expect(message).not.toContain('SQL boom');
    },
  );
});

describe('describeHttpError for load-reservations', () => {
  // Texte propre à la liste des réservations, distinct de celui des véhicules et des utilisateurs
  it('describes missing rights on a 403 when loading the reservations list', () => {
    const message = describeHttpError(httpError(403), 'load-reservations');

    expect(message).toBe("Vous n'avez pas accès à la liste des réservations.");
    expect(message).not.toBe(describeHttpError(httpError(403), 'load-vehicles'));
    expect(message).not.toBe(describeHttpError(httpError(403), 'load-users'));
  });

  // Les autres statuts donnent le message générique, jamais le corps de l'API
  it.each([404, 418, 500])(
    'returns exactly the generic message and never the API body for load-reservations with status %i',
    (status: number) => {
      const message = describeHttpError(
        httpError(status, { message: 'SQL boom' }),
        'load-reservations',
      );

      expect(message).toBe('Une erreur est survenue');
      expect(message).not.toContain('SQL boom');
    },
  );
});

describe('describeHttpError for load-dashboard', () => {
  // Texte propre au tableau de bord, distinct de celui des trois listes
  it('describes missing rights on a 403 when loading the dashboard', () => {
    const message = describeHttpError(httpError(403), 'load-dashboard');

    expect(message).toBe("Vous n'avez pas accès au tableau de bord.");
    expect(message).not.toBe(describeHttpError(httpError(403), 'load-vehicles'));
    expect(message).not.toBe(describeHttpError(httpError(403), 'load-users'));
    expect(message).not.toBe(describeHttpError(httpError(403), 'load-reservations'));
  });

  // Les autres statuts donnent le message générique, jamais le corps de l'API
  it.each([404, 418, 500])(
    'returns exactly the generic message and never the API body for load-dashboard with status %i',
    (status: number) => {
      const message = describeHttpError(
        httpError(status, { message: 'SQL boom' }),
        'load-dashboard',
      );

      expect(message).toBe('Une erreur est survenue');
      expect(message).not.toContain('SQL boom');
    },
  );
});

const GENERIC_400_TEXT = "Les informations saisies n'ont pas pu être validées. Veuillez réessayer.";
const CREATE_VEHICLE_FORBIDDEN_TEXT = "Vous n'avez pas le droit de créer un véhicule.";
const CREATE_USER_FORBIDDEN_TEXT = "Vous n'avez pas le droit de créer cet utilisateur.";
const CREATE_USER_CONFLICT_TEXT = 'Cette adresse email est déjà utilisée.';

describe('describeHttpError for create-vehicle', () => {
  it('describes a validation failure on a 400 with the generic text', () => {
    expect(describeHttpError(httpError(400), 'create-vehicle')).toBe(GENERIC_400_TEXT);
  });

  it('describes missing rights on a 403 when creating a vehicle', () => {
    const message = describeHttpError(httpError(403), 'create-vehicle');

    expect(message).toBe(CREATE_VEHICLE_FORBIDDEN_TEXT);
    expect(message).not.toBe(describeHttpError(httpError(403), 'delete-vehicle'));
  });

  // Les autres statuts donnent le message générique, jamais le corps de l'API
  it.each([404, 409, 418, 500])(
    'returns exactly the generic message and never the API body for create-vehicle with status %i',
    (status: number) => {
      const message = describeHttpError(httpError(status, 'SQL boom'), 'create-vehicle');

      expect(message).toBe('Une erreur est survenue');
      expect(message).not.toContain('SQL boom');
    },
  );
});

describe('describeHttpError for create-user', () => {
  it('describes a validation failure on a 400 with the generic text', () => {
    expect(describeHttpError(httpError(400), 'create-user')).toBe(GENERIC_400_TEXT);
  });

  it('describes missing rights on a 403 when creating a user, distinct from the vehicle text', () => {
    const message = describeHttpError(httpError(403), 'create-user');

    expect(message).toBe(CREATE_USER_FORBIDDEN_TEXT);
    expect(message).not.toBe(describeHttpError(httpError(403), 'create-vehicle'));
  });

  // Le texte est fixe : le corps texte brut de l'API n'est jamais affiché
  it('describes an already used email on a 409, never the API body', () => {
    const message = describeHttpError(httpError(409, 'Email already exists'), 'create-user');

    expect(message).toBe(CREATE_USER_CONFLICT_TEXT);
    expect(message).not.toContain('Email already exists');
  });

  it.each([404, 418, 500])(
    'returns exactly the generic message and never the API body for create-user with status %i',
    (status: number) => {
      const message = describeHttpError(httpError(status, 'SQL boom'), 'create-user');

      expect(message).toBe('Une erreur est survenue');
      expect(message).not.toContain('SQL boom');
    },
  );
});

describe('describeHttpError for create-reservation', () => {
  it('describes a validation failure on a 400 with the generic text', () => {
    expect(describeHttpError(httpError(400), 'create-reservation')).toBe(GENERIC_400_TEXT);
  });

  it('describes missing rights on a 403, distinct from the vehicle and user texts', () => {
    const message = describeHttpError(httpError(403), 'create-reservation');

    expect(message).toBe("Vous n'avez pas le droit de créer une réservation.");
    expect(message).not.toBe(describeHttpError(httpError(403), 'create-vehicle'));
    expect(message).not.toBe(describeHttpError(httpError(403), 'create-user'));
  });

  it('describes a vanished vehicle or driver on a 404, never the API body', () => {
    const message = describeHttpError(httpError(404, 'Vehicle not found'), 'create-reservation');

    expect(message).toBe("Ce véhicule ou ce conducteur n'existe plus.");
    expect(message).not.toContain('Vehicle not found');
  });

  it('describes an unavailable vehicle on a 409, never the API body', () => {
    const message = describeHttpError(
      httpError(409, 'Véhicule non disponible'),
      'create-reservation',
    );

    expect(message).toBe("Ce véhicule n'est pas disponible sur cette période.");
    expect(message).not.toContain('Véhicule non disponible');
  });

  it.each([0, 401, 418, 429, 500])(
    'returns exactly the generic message and never the API body for create-reservation with status %i',
    (status: number) => {
      const message = describeHttpError(httpError(status, 'SQL boom'), 'create-reservation');

      expect(message).toBe('Une erreur est survenue');
      expect(message).not.toContain('SQL boom');
    },
  );
});

describe('httpErrorContext', () => {
  it('lists 400, 403, 404 and 409 as handled for create-reservation and leaves the global statuses out', () => {
    const handled = httpErrorContext('create-reservation').get(HANDLED_STATUSES);

    for (const status of [400, 403, 404, 409]) {
      expect(handled).toContain(status);
    }
    for (const status of [0, 401, 429, 500]) {
      expect(handled).not.toContain(status);
    }
  });

  it('lists 400 and 403 as handled for create-vehicle and leaves the global statuses out', () => {
    const handled = httpErrorContext('create-vehicle').get(HANDLED_STATUSES);

    expect(handled).toContain(400);
    expect(handled).toContain(403);
    for (const status of [0, 401, 429, 500]) {
      expect(handled).not.toContain(status);
    }
  });

  it('lists 400, 403 and 409 as handled for create-user and leaves the global statuses out', () => {
    const handled = httpErrorContext('create-user').get(HANDLED_STATUSES);

    expect(handled).toContain(400);
    expect(handled).toContain(403);
    expect(handled).toContain(409);
    for (const status of [0, 401, 429, 500]) {
      expect(handled).not.toContain(status);
    }
  });

  // Seul le 403 est géré par l'écran, les autres statuts restent à l'intercepteur
  it('declares only 403 as handled for load-dashboard', () => {
    expect(httpErrorContext('load-dashboard').get(HANDLED_STATUSES)).toEqual([403]);
  });

  // Seul le 403 est géré par l'écran, les autres statuts restent à l'intercepteur
  it('declares only 403 as handled for load-reservations', () => {
    expect(httpErrorContext('load-reservations').get(HANDLED_STATUSES)).toEqual([403]);
  });

  // Seul le 403 est géré par l'écran, les autres statuts restent à l'intercepteur
  it('declares only 403 as handled for load-users', () => {
    expect(httpErrorContext('load-users').get(HANDLED_STATUSES)).toEqual([403]);
  });

  // Seuls 403, 404 et 409 ont un texte propre, les autres statuts restent à l'intercepteur
  it('lists 403, 404 and 409 as handled for delete-vehicle and leaves the global statuses out', () => {
    const handled = httpErrorContext('delete-vehicle').get(HANDLED_STATUSES);

    expect(handled).toContain(403);
    expect(handled).toContain(404);
    expect(handled).toContain(409);
    for (const status of [401, 429, 0, 500]) {
      expect(handled).not.toContain(status);
    }
  });
});
