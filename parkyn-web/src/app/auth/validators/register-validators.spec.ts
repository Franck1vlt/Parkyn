import { FormControl, FormGroup } from '@angular/forms';
import { nameFormat, notBlank, passwordMatch } from './register-validators';

describe('register validators', () => {
  describe('notBlank', () => {
    // Vide, espaces seuls et null sont refusés
    it.each([
      ['empty', ''],
      ['spaces only', '   '],
      ['null', null],
    ])('flags a %s value as blank', (_label, value) => {
      expect(notBlank(new FormControl(value))).toEqual({ blank: true });
    });

    // Une valeur non vide est acceptée
    it('accepts a real value', () => {
      expect(notBlank(new FormControl('a'))).toBeNull();
    });
  });

  describe('nameFormat', () => {
    // Tirets, apostrophes, accents, espaces et alphabets non latins sont acceptés
    it.each(['Jean-Pierre', "O'Brien", 'O’Brien', 'Éloïse', 'Anne Marie', '李', 'Zoë'])(
      'accepts the name %s',
      (name) => {
        expect(nameFormat(new FormControl(name))).toBeNull();
      },
    );

    // Chiffres, ponctuation seule, symboles et emoji sont refusés
    it.each(['Jean3', '1234', '-', "'", ' - ', 'Jean_', 'Jean.', 'J@n', 'Jean😀'])(
      'rejects the name %s',
      (name) => {
        expect(nameFormat(new FormControl(name))).toEqual({ nameFormat: true });
      },
    );

    // La valeur vide est laissée à required
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

    // Deux mots de passe identiques sont acceptés
    it('accepts identical passwords', () => {
      expect(passwordMatch(group('un-mot-de-passe', 'un-mot-de-passe'))).toBeNull();
    });

    // Deux mots de passe différents donnent l'erreur passwordMismatch
    it('flags different passwords', () => {
      expect(passwordMatch(group('un-mot-de-passe', 'un-autre-mot'))).toEqual({
        passwordMismatch: true,
      });
    });

    // Deux champs vides : l'erreur est laissée à required
    it('accepts two empty fields', () => {
      expect(passwordMatch(group('', ''))).toBeNull();
    });
  });
});
