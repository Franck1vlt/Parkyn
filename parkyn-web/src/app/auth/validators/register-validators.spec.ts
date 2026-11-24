import { FormControl, FormGroup } from '@angular/forms';
import { nameFormat, notBlank, passwordMatch } from './register-validators';

describe('register validators', () => {
  describe('notBlank', () => {
    // B1 : vide, espaces seuls et null sont refusés
    it.each([
      ['empty', ''],
      ['spaces only', '   '],
      ['null', null],
    ])('flags a %s value as blank', (_label, value) => {
      expect(notBlank(new FormControl(value))).toEqual({ blank: true });
    });

    // B1 : une valeur réelle passe
    it('accepts a real value', () => {
      expect(notBlank(new FormControl('a'))).toBeNull();
    });
  });

  describe('nameFormat', () => {
    // B2 : garde-fou contre une expression trop stricte
    it.each(['Jean-Pierre', "O'Brien", 'O’Brien', 'Éloïse', 'Anne Marie', '李', 'Zoë'])(
      'accepts the name %s',
      (name) => {
        expect(nameFormat(new FormControl(name))).toBeNull();
      },
    );

    // B3 : chiffres, ponctuation seule, symboles et emoji sont refusés
    it.each(['Jean3', '1234', '-', "'", ' - ', 'Jean_', 'Jean.', 'J@n', 'Jean😀'])(
      'rejects the name %s',
      (name) => {
        expect(nameFormat(new FormControl(name))).toEqual({ nameFormat: true });
      },
    );

    // B4 : la valeur vide est laissée à required
    it.each([
      ['empty', ''],
      ['null', null],
    ])('ignores a %s value', (_label, value) => {
      expect(nameFormat(new FormControl(value))).toBeNull();
    });
  });

  describe('passwordMatch', () => {
    function group(password: string, confirmPassword: string): FormGroup {
      return new FormGroup({
        password: new FormControl(password),
        confirmPassword: new FormControl(confirmPassword),
      });
    }

    // B5 : identiques
    it('accepts identical passwords', () => {
      expect(passwordMatch(group('un-mot-de-passe', 'un-mot-de-passe'))).toBeNull();
    });

    // B5 : différents
    it('flags different passwords', () => {
      expect(passwordMatch(group('un-mot-de-passe', 'un-autre-mot'))).toEqual({
        passwordMismatch: true,
      });
    });

    // B6 : deux champs vides, c'est required qui parle
    it('accepts two empty fields', () => {
      expect(passwordMatch(group('', ''))).toBeNull();
    });
  });
});
