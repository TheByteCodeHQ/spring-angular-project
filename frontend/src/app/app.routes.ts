import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', canActivate: [authGuard], loadComponent: () => import('./tasks/task-list').then((m) => m.TaskList) },
  { path: 'login', loadComponent: () => import('./login/login').then((m) => m.Login) },
  { path: '**', redirectTo: '' },
];
