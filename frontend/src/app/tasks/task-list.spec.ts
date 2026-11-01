import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TOKEN_KEY, makeToken, pageOf, task } from '../../testing';
import { authInterceptor } from '../core/auth.interceptor';
import { TaskList } from './task-list';

describe('TaskList', () => {
  let fixture: ComponentFixture<TaskList>;
  let mock: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const rows = () => Array.from(el().querySelectorAll('tbody tr')).filter((r) => !r.querySelector('.empty'));
  const listRequest = (): TestRequest => mock.expectOne((r) => r.url === '/api/tasks' && r.method === 'GET');
  const settle = async () => { fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges(); };
  const type = (selector: string, value: string) => {
    const input = el().querySelector(selector) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };

  async function start(role = 'USER', tasks = [task({ id: 'a', title: 'First' }), task({ id: 'b', title: 'Second', status: 'DONE' })]) {
    sessionStorage.clear();
    sessionStorage.setItem(TOKEN_KEY, makeToken({ roles: [role] }));
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    mock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TaskList);
    fixture.detectChanges();
    listRequest().flush(pageOf(tasks));
    await settle();
  }

  afterEach(() => mock.verify());

  it('loads the first page on start and shows the tasks with their status', async () => {
    await start();
    expect(rows()).toHaveLength(2);
    expect(rows()[0].textContent).toContain('First');
    expect((rows()[1].querySelector('select') as HTMLSelectElement).value).toBe('DONE');
    expect(el().textContent).toContain('Page 1 of 1 (2 tasks)');
  });

  it('shows an empty state', async () => {
    await start('USER', []);
    expect(el().querySelector('.empty')?.textContent).toContain('No tasks match.');
  });

  it('shows task text as text, never as markup', async () => {
    await start('USER', [task({ title: '<img src=x onerror=alert(1)>' })]);
    expect(el().querySelector('tbody img')).toBeNull();
    expect(rows()[0].textContent).toContain('<img src=x onerror=alert(1)>');
  });

  it('only offers delete to administrators', async () => {
    await start('USER');
    expect(el().querySelector('button[aria-label^="Delete"]')).toBeNull();
    mock.verify();
    TestBed.resetTestingModule();
    await start('ADMIN');
    expect(el().querySelectorAll('button[aria-label^="Delete"]')).toHaveLength(2);
  });

  it('asks for confirmation before deleting and reloads afterwards', async () => {
    await start('ADMIN');
    (el().querySelector('button[aria-label="Delete First"]') as HTMLButtonElement).click();
    await settle();
    mock.expectNone((r) => r.method === 'DELETE');                       // nothing is deleted by the first click
    const confirm = Array.from(el().querySelectorAll('button')).find((b) => b.textContent?.includes('Confirm delete')) as HTMLButtonElement;
    confirm.click();
    mock.expectOne((r) => r.method === 'DELETE' && r.url === '/api/tasks/a').flush(null, { status: 204, statusText: 'No Content' });
    listRequest().flush(pageOf([task({ id: 'b', title: 'Second' })]));
    await settle();
    expect(rows()).toHaveLength(1);
  });

  it('can back out of a delete', async () => {
    await start('ADMIN');
    (el().querySelector('button[aria-label="Delete First"]') as HTMLButtonElement).click();
    await settle();
    (Array.from(el().querySelectorAll('button')).find((b) => b.textContent === 'Cancel') as HTMLButtonElement).click();
    await settle();
    expect(el().textContent).not.toContain('Confirm delete');
    mock.expectNone((r) => r.method === 'DELETE');
  });

  it('searches with the typed text, and filters by status from page one', async () => {
    await start();
    type('input[name="q"]', 'login');
    const search = el().querySelectorAll('form')[1] as HTMLFormElement;
    search.dispatchEvent(new Event('submit'));
    const searched = listRequest();
    expect(searched.request.params.get('q')).toBe('login');
    expect(searched.request.params.get('page')).toBe('0');
    searched.flush(pageOf([task()]));
    await settle();

    const select = el().querySelector('select[name="status"]') as HTMLSelectElement;
    select.value = 'DONE';
    select.dispatchEvent(new Event('change'));
    const filtered = listRequest();
    expect(filtered.request.params.get('status')).toBe('DONE');
    filtered.flush(pageOf([]));
  });

  const addButton = () => Array.from(el().querySelectorAll('button')).find((b) => b.textContent === 'Add task') as HTMLButtonElement;
  const titleInput = () => el().querySelector('input[name="title"]') as HTMLInputElement;
  const submitNewTask = () => (el().querySelectorAll('form')[0] as HTMLFormElement).dispatchEvent(new Event('submit'));
  const createRequest = () => mock.expectOne((r) => r.method === 'POST' && r.url === '/api/tasks');

  it('creates a task and clears the form', async () => {
    await start();
    expect(addButton().disabled).toBe(true);                              // nothing typed yet
    type('input[name="title"]', '  Ship it  ');
    type('input[name="due"]', '2030-05-01');
    await settle();
    expect(addButton().disabled).toBe(false);
    submitNewTask();
    await settle();
    expect(titleInput().value).toBe('');                                  // cleared at once, not when the server replies
    const created = createRequest();
    expect(created.request.body).toEqual({ title: 'Ship it', dueDate: '2030-05-01' });
    created.flush(task({ title: 'Ship it' }), { status: 201, statusText: 'Created' });
    listRequest().flush(pageOf([task({ title: 'Ship it' })]));
    await settle();
    expect(titleInput().value).toBe('');
  });

  it('keeps what the user types for the next task while the previous one is still being saved', async () => {
    await start();
    type('input[name="title"]', 'First');
    submitNewTask();
    await settle();
    type('input[name="title"]', 'Second');                                // typed before the server has replied
    createRequest().flush(task({ title: 'First' }), { status: 201, statusText: 'Created' });
    listRequest().flush(pageOf([task({ title: 'First' })]));
    await settle();
    expect(titleInput().value).toBe('Second');
    expect(addButton().disabled).toBe(false);
  });

  it('gives the text back when saving fails, so nothing the user typed is lost', async () => {
    await start();
    type('input[name="title"]', 'Important');
    submitNewTask();
    await settle();
    createRequest().flush({ detail: 'Validation failed' }, { status: 400, statusText: 'Bad Request' });
    await settle();
    expect(titleInput().value).toBe('Important');
    expect(el().querySelector('[role="alert"]')?.textContent).toContain('Validation failed');
  });

  it('does not overwrite something newer with the restored text after a failure', async () => {
    await start();
    type('input[name="title"]', 'Old');
    submitNewTask();
    await settle();
    type('input[name="title"]', 'Newer');
    createRequest().flush({}, { status: 500, statusText: 'Server Error' });
    await settle();
    expect(titleInput().value).toBe('Newer');
  });

  it('changes a status through the API and reloads the current page', async () => {
    await start();
    const select = rows()[0].querySelector('select') as HTMLSelectElement;
    select.value = 'IN_PROGRESS';
    select.dispatchEvent(new Event('change'));
    const patch = mock.expectOne((r) => r.method === 'PATCH' && r.url === '/api/tasks/a/status');
    expect(patch.request.body).toEqual({ status: 'IN_PROGRESS' });
    patch.flush(task({ status: 'IN_PROGRESS' }));
    listRequest().flush(pageOf([task({ id: 'a', status: 'IN_PROGRESS' })]));
  });

  it('shows the server problem and the request id when something fails', async () => {
    await start();
    (el().querySelectorAll('form')[1] as HTMLFormElement).dispatchEvent(new Event('submit'));
    listRequest().flush({ title: 'Invalid request', detail: 'Cannot sort by x', requestId: 'req-42' },
      { status: 400, statusText: 'Bad Request' });
    await settle();
    expect(el().querySelector('[role="alert"]')?.textContent).toBe('Cannot sort by x (request req-42)');
  });

  it('pages forwards and backwards', async () => {
    sessionStorage.clear();
    sessionStorage.setItem(TOKEN_KEY, makeToken({}));
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    mock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TaskList);
    fixture.detectChanges();
    listRequest().flush(pageOf([task()], { page: 0, totalPages: 3, totalElements: 25 }));
    await settle();
    const button = (label: string) => Array.from(el().querySelectorAll('button')).find((b) => b.textContent === label) as HTMLButtonElement;
    expect(button('Previous').disabled).toBe(true);
    button('Next').click();
    const next = listRequest();
    expect(next.request.params.get('page')).toBe('1');
    next.flush(pageOf([task()], { page: 1, totalPages: 3, totalElements: 25 }));
    await settle();
    expect(el().textContent).toContain('Page 2 of 3 (25 tasks)');
    expect(button('Previous').disabled).toBe(false);
  });
});
