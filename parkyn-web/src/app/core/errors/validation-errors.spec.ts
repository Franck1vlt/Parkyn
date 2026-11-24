import { parseValidationErrors } from './validation-errors';

describe('parseValidationErrors', () => {
  // A1 : corps nominal
  it('parses a body with one message on one field', () => {
    expect(parseValidationErrors({ errors: { email: ['x'] } })).toEqual({
      fields: { email: ['x'] },
      global: [],
    });
  });

  // A2 : plusieurs messages et plusieurs champs
  it('keeps several messages and several fields in order', () => {
    expect(
      parseValidationErrors({ errors: { email: ['a', 'b'], password: ['c'], firstname: ['d'] } }),
    ).toEqual({
      fields: { email: ['a', 'b'], password: ['c'], firstname: ['d'] },
      global: [],
    });
  });

  // A3 : _global va dans global et pas dans fields
  it('moves the _global messages to global and out of fields', () => {
    expect(parseValidationErrors({ errors: { _global: ['g1', 'g2'], email: ['x'] } })).toEqual({
      fields: { email: ['x'] },
      global: ['g1', 'g2'],
    });
  });

  // A4 : une clé inconnue reste dans fields, le composant décide
  it('keeps an unknown key in fields', () => {
    expect(parseValidationErrors({ errors: { confirmPassword: ['y'] } })).toEqual({
      fields: { confirmPassword: ['y'] },
      global: [],
    });
  });

  // A5 : corps inexploitables
  it.each([
    ['null', null],
    ['undefined', undefined],
    ['a string', 'texte'],
    ['a number', 42],
    ['an array', []],
    ['an empty object', {}],
    ['errors null', { errors: null }],
    ['errors as an array', { errors: [] }],
    ['errors empty', { errors: {} }],
  ])('returns null for an unusable body (%s)', (_label, body) => {
    expect(parseValidationErrors(body)).toBeNull();
  });
});
