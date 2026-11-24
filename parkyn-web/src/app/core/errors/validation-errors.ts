export interface ServerValidationErrors {
  fields: Record<string, string[]>;
  global: string[];
}

const GLOBAL_KEY = '_global';

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

// Extrait les messages du corps { errors: { champ: [messages] } } d'un 400 ; null si rien d'exploitable
export function parseValidationErrors(body: unknown): ServerValidationErrors | null {
  if (!isPlainObject(body) || !isPlainObject(body['errors'])) return null;

  const fields: [string, string[]][] = [];
  let global: string[] = [];

  for (const [key, value] of Object.entries(body['errors'])) {
    if (!Array.isArray(value)) continue;
    const messages = value.filter((item): item is string => typeof item === 'string');
    if (messages.length === 0) continue;
    if (key === GLOBAL_KEY) global = messages;
    else fields.push([key, messages]);
  }

  if (fields.length === 0 && global.length === 0) return null;
  return { fields: Object.fromEntries(fields), global };
}
