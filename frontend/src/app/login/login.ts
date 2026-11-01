import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { describeError } from '../core/problem';

@Component({
  selector: 'app-login',
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly username = signal('');
  protected readonly role = signal<'USER' | 'ADMIN'>('USER');
  protected readonly error = signal('');
  protected readonly busy = signal(false);

  protected onUsername(event: Event): void {
    this.username.set((event.target as HTMLInputElement).value);
  }

  protected onRole(event: Event): void {
    this.role.set((event.target as HTMLSelectElement).value as 'USER' | 'ADMIN');
  }

  protected async submit(event: Event): Promise<void> {
    event.preventDefault();
    const name = this.username().trim();
    if (!name || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set('');
    try {
      await this.auth.login(name, [this.role()]);
      await this.router.navigate(['/']);
    } catch (err) {
      this.error.set(
        err instanceof HttpErrorResponse && err.status === 404
          ? 'The development login is not available. Start the backend with the dev profile.'
          : describeError(err),
      );
    } finally {
      this.busy.set(false);
    }
  }
}
