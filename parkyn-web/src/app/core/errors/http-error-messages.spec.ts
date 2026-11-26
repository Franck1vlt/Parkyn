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

describe('httpErrorContext', () => {
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
