import { HttpErrorResponse } from '@angular/common/http';
import { HANDLED_STATUSES } from '../interceptors/error-handling';
import { describeHttpError, HttpAction, httpErrorContext } from './http-error-messages';

const NOT_FOUND_TEXT = "Ce véhicule n'existe plus.";
const DELETE_FORBIDDEN_TEXT = "Vous n'avez pas le droit de supprimer ce véhicule.";
const LOAD_FORBIDDEN_TEXT = "Vous n'avez pas accès à la liste des véhicules.";

function httpError(status: number, body: unknown = null): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: body });
}

describe('describeHttpError', () => {
  // Cas 1
  it('describes a vanished vehicle on a 404 when deleting', () => {
    expect(describeHttpError(httpError(404), 'delete-vehicle')).toBe(NOT_FOUND_TEXT);
  });

  // Cas 2
  it('describes missing rights on a 403 when deleting, distinct from the 404 text', () => {
    const message = describeHttpError(httpError(403), 'delete-vehicle');

    expect(message).toBe(DELETE_FORBIDDEN_TEXT);
    expect(message).not.toBe(describeHttpError(httpError(404), 'delete-vehicle'));
  });

  // Cas 3
  it('words a 403 differently for loading the list and for deleting', () => {
    const load = describeHttpError(httpError(403), 'load-vehicles');

    expect(load).toBe(LOAD_FORBIDDEN_TEXT);
    expect(load).not.toBe(describeHttpError(httpError(403), 'delete-vehicle'));
  });

  // Cas 4 : repli générique exact, jamais le corps de l'API
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

describe('httpErrorContext', () => {
  // Cas 5 : seuls 403 et 404 ont un texte, le reste reste à l'intercepteur
  it('lists 403 and 404 as handled for delete-vehicle and leaves the global statuses out', () => {
    const handled = httpErrorContext('delete-vehicle').get(HANDLED_STATUSES);

    expect(handled).toContain(403);
    expect(handled).toContain(404);
    for (const status of [401, 429, 0, 500]) {
      expect(handled).not.toContain(status);
    }
  });
});
