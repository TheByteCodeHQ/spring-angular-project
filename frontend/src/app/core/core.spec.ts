import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TOKEN_KEY, makeToken, pageOf, task } from '../../testing';
import { authGuard } from './auth.guard';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { describeError } from './problem';
import { TaskService } from './task.service';

function setup() {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
  });
  return { http: TestBed.inject(HttpClient), mock: TestBed.inject(HttpTestingController), auth: TestBed.inject(AuthService) };
}

describe('describeError', () => {
  const problem = (status: number, body: object) => new HttpErrorResponse({ status, error: body });

  it('shows the detail, the field errors and the request id', () => {
    const message = describeError(problem(400, {
      title: 'Validation failed', detail: 'One or more fields are invalid', requestId: 'abc-123',
      errors: [{ field: 'title', message: 'must not be blank' }],
    }));
    expect(message).toBe('One or more fields are invalid - title: must not be blank (request abc-123)');
  });

  it('falls back to the status when the body is not a problem document', () => {
    expect(describeError(new HttpErrorResponse({ status: 502, error: '<html>bad gateway</html>' }))).toBe('Request failed (502)');
  });

  it('explains a network failure and survives unknown errors', () => {
    expect(describeError(new HttpErrorResponse({ status: 0 }))).toContain('Cannot reach the server');
    expect(describeError(new Error('boom'))).toBe('Something went wrong. Please try again.');
    expect(describeError(undefined)).toBe('Something went wrong. Please try again.');
  });
});

describe('AuthService', () => {
  beforeEach(() => sessionStorage.clear());

  it('starts signed out', () => {
    const { auth } = setup();
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.username()).toBe('');
    expect(auth.isAdmin()).toBe(false);
  });

  it('stores the token from the dev login and reads the claims', async () => {
    const { auth, mock } = setup();
    const done = auth.login('root', ['ADMIN']);
    const request = mock.expectOne('/api/dev/token');
    expect(request.request.body).toEqual({ username: 'root', roles: ['ADMIN'] });
    request.flush({ accessToken: makeToken({ sub: 'root', roles: ['ADMIN'] }), expiresIn: 3600 });
    await done;
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.username()).toBe('root');
    expect(auth.isAdmin()).toBe(true);
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeTruthy();
  });

  it('restores a stored session and clears it on logout', () => {
    sessionStorage.setItem(TOKEN_KEY, makeToken({ sub: 'bob' }));
    const { auth } = setup();
    expect(auth.username()).toBe('bob');
    auth.logout();
    expect(auth.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it('treats an expired or garbled token as signed out', () => {
    sessionStorage.setItem(TOKEN_KEY, makeToken({ expInSeconds: -60 }));
    expect(setup().auth.isAuthenticated()).toBe(false);
    TestBed.resetTestingModule();
    sessionStorage.setItem(TOKEN_KEY, 'not-a-jwt');
    const { auth } = setup();
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.username()).toBe('');
  });
});

describe('authInterceptor', () => {
  beforeEach(() => sessionStorage.clear());

  it('adds the bearer token to API calls only', () => {
    sessionStorage.setItem(TOKEN_KEY, makeToken({}));
    const { http, mock, auth } = setup();
    http.get('/api/tasks').subscribe();
    expect(mock.expectOne('/api/tasks').request.headers.get('Authorization')).toBe(`Bearer ${auth.token()}`);
    http.get('https://elsewhere.example/api/tasks').subscribe();
    expect(mock.expectOne('https://elsewhere.example/api/tasks').request.headers.has('Authorization')).toBe(false);
    http.post('/api/dev/token', {}).subscribe();
    expect(mock.expectOne('/api/dev/token').request.headers.has('Authorization')).toBe(false);
  });

  it('signs the user out and goes to the login page on a 401', () => {
    sessionStorage.setItem(TOKEN_KEY, makeToken({}));
    const { http, mock, auth } = setup();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    let failed = 0;
    http.get('/api/tasks').subscribe({ error: () => failed++ });
    mock.expectOne('/api/tasks').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(failed).toBe(1);               // the caller still sees the error
    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('does not sign out on other errors', () => {
    sessionStorage.setItem(TOKEN_KEY, makeToken({}));
    const { http, mock, auth } = setup();
    http.get('/api/tasks').subscribe({ error: () => undefined });
    mock.expectOne('/api/tasks').flush({}, { status: 403, statusText: 'Forbidden' });
    expect(auth.isAuthenticated()).toBe(true);
  });
});

describe('authGuard', () => {
  beforeEach(() => sessionStorage.clear());

  it('lets a signed-in user through and sends everyone else to the login page', () => {
    setup();
    const run = () => TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    expect(String(run())).toBe('/login');
    sessionStorage.setItem(TOKEN_KEY, makeToken({}));
    TestBed.resetTestingModule();
    setup();
    expect(run()).toBe(true);
  });
});

describe('TaskService', () => {
  beforeEach(() => sessionStorage.clear());

  it('sends only the filters that are set', () => {
    const { mock } = setup();
    const tasks = TestBed.inject(TaskService);
    tasks.list({ status: '', q: '  ', page: 2, size: 10 }).subscribe();
    const plain = mock.expectOne((r) => r.url === '/api/tasks');
    expect(plain.request.params.keys().sort()).toEqual(['page', 'size']);
    plain.flush(pageOf([]));

    tasks.list({ status: 'DONE', q: ' login ', page: 0, size: 5 }).subscribe();
    const filtered = mock.expectOne((r) => r.url === '/api/tasks');
    expect(filtered.request.params.get('status')).toBe('DONE');
    expect(filtered.request.params.get('q')).toBe('login');
    filtered.flush(pageOf([task()]));
  });

  it('uses the right verbs and encodes ids', () => {
    const { mock } = setup();
    const tasks = TestBed.inject(TaskService);
    tasks.create({ title: 'x', dueDate: null }).subscribe();
    expect(mock.expectOne('/api/tasks').request.method).toBe('POST');
    tasks.changeStatus('a/b', 'DONE').subscribe();
    const patch = mock.expectOne('/api/tasks/a%2Fb/status');
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body).toEqual({ status: 'DONE' });
    tasks.remove('id-9').subscribe();
    expect(mock.expectOne('/api/tasks/id-9').request.method).toBe('DELETE');
  });
});
