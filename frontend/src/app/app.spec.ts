import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TOKEN_KEY, makeToken } from '../testing';
import { App } from './app';
import { Login } from './login/login';

describe('App shell', () => {
  async function render(token?: string) {
    sessionStorage.clear();
    if (token) {
      sessionStorage.setItem(TOKEN_KEY, token);
    }
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'login', component: Login }]), provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('shows only the title when signed out', async () => {
    const fixture = await render();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('h1')?.textContent).toBe('Task Manager');
    expect(page.querySelector('button')).toBeNull();
  });

  it('shows who is signed in, marks admins, and signs out', async () => {
    const fixture = await render(makeToken({ sub: 'root', roles: ['ADMIN'] }));
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('.who')?.textContent).toContain('root');
    expect(page.querySelector('.badge')?.textContent).toBe('admin');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    (page.querySelector('button') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
