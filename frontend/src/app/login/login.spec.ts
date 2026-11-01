import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TOKEN_KEY, makeToken } from '../../testing';
import { Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let mock: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  const el = () => fixture.nativeElement as HTMLElement;
  const submit = () => (el().querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit', { cancelable: true }));
  const type = (value: string) => {
    const input = el().querySelector('input[name="username"]') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };

  beforeEach(async () => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    mock = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(Login);
    await fixture.whenStable();
    fixture.detectChanges();
  });

  afterEach(() => mock.verify());

  it('does nothing until a name is typed', async () => {
    expect((el().querySelector('button') as HTMLButtonElement).disabled).toBe(true);
    submit();
    mock.expectNone('/api/dev/token');
  });

  it('signs in with the chosen role and goes to the task list', async () => {
    type('  carol ');
    const select = el().querySelector('select') as HTMLSelectElement;
    select.value = 'ADMIN';
    select.dispatchEvent(new Event('change'));
    submit();
    const request = mock.expectOne('/api/dev/token');
    expect(request.request.body).toEqual({ username: 'carol', roles: ['ADMIN'] });
    request.flush({ accessToken: makeToken({ sub: 'carol', roles: ['ADMIN'] }), expiresIn: 3600 });
    await fixture.whenStable();
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeTruthy();
    expect(navigate).toHaveBeenCalledWith(['/']);
  });

  it('explains when the dev login is not available', async () => {
    type('dave');
    submit();
    mock.expectOne('/api/dev/token').flush({}, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el().querySelector('[role="alert"]')?.textContent).toContain('Start the backend with the dev profile');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('shows the server message for other failures', async () => {
    type('erin');
    submit();
    mock.expectOne('/api/dev/token').flush({ detail: 'Nope', requestId: 'r-1' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el().querySelector('[role="alert"]')?.textContent).toBe('Nope (request r-1)');
  });
});
