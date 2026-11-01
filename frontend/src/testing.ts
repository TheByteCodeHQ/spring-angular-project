/** Helpers shared by the specs. */

function base64Url(value: object): string {
  return btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** An unsigned token that looks like the real thing: enough for code that only reads the claims. */
export function makeToken(claims: { sub?: string; roles?: string[]; expInSeconds?: number }): string {
  const exp = Math.floor(Date.now() / 1000) + (claims.expInSeconds ?? 3600);
  return `${base64Url({ alg: 'HS256', typ: 'JWT' })}.${base64Url({ sub: claims.sub ?? 'alice', roles: claims.roles ?? ['USER'], exp })}.signature`;
}

export const TOKEN_KEY = 'task-app.token';

export function task(overrides: Partial<import('./app/core/models').Task> = {}): import('./app/core/models').Task {
  return {
    id: 'id-1', title: 'Write tests', description: null, status: 'TODO', dueDate: null, createdBy: 'alice',
    createdAt: '2026-01-01T10:00:00Z', updatedAt: '2026-01-01T10:00:00Z', version: 0, ...overrides,
  };
}

export function pageOf<T>(content: T[], extra: Partial<{ page: number; totalPages: number; totalElements: number }> = {}) {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: content.length ? 1 : 0, ...extra };
}
