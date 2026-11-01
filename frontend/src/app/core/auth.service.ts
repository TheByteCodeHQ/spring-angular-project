import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

interface TokenResponse {
  accessToken: string;
  expiresIn: number;
}

interface Claims {
  sub?: string;
  exp?: number;
  roles?: string[];
}

const STORAGE_KEY = 'task-app.token';

function readStored(): string | null {
  try {
    return sessionStorage.getItem(STORAGE_KEY);
  } catch {
    return null; // storage can be blocked; the app then simply asks for a login each time
  }
}

function decode(token: string | null): Claims | null {
  if (!token) {
    return null;
  }
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(payload)) as Claims;
  } catch {
    return null;
  }
}

/**
 * Holds the bearer token. The token is only used to decide what to *show* (for example the delete button).
 * The server enforces every permission, so tampering with it here gains nothing.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly tokenState = signal<string | null>(readStored());

  readonly token = this.tokenState.asReadonly();
  private readonly claims = computed(() => decode(this.tokenState()));
  readonly username = computed(() => this.claims()?.sub ?? '');
  readonly isAdmin = computed(() => this.claims()?.roles?.includes('ADMIN') ?? false);

  isAuthenticated(): boolean {
    const exp = this.claims()?.exp;
    return exp !== undefined && exp * 1000 > Date.now();
  }

  /** Development login: asks the dev-profile backend to mint a token. A real deployment signs in through an identity provider. */
  async login(username: string, roles: string[]): Promise<void> {
    const response = await firstValueFrom(this.http.post<TokenResponse>('/api/dev/token', { username, roles }));
    this.setToken(response.accessToken);
  }

  logout(): void {
    this.setToken(null);
  }

  private setToken(token: string | null): void {
    this.tokenState.set(token);
    try {
      if (token) {
        sessionStorage.setItem(STORAGE_KEY, token);
      } else {
        sessionStorage.removeItem(STORAGE_KEY);
      }
    } catch {
      /* storage unavailable: the in-memory signal still works for this session */
    }
  }
}
