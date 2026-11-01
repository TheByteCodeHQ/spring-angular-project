import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { NewTask, Page, Task, TaskQuery, TaskStatus } from './models';

@Injectable({ providedIn: 'root' })
export class TaskService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/tasks';

  list(query: TaskQuery): Observable<Page<Task>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.status) {
      params = params.set('status', query.status);
    }
    if (query.q.trim()) {
      params = params.set('q', query.q.trim());
    }
    return this.http.get<Page<Task>>(this.base, { params });
  }

  create(task: NewTask): Observable<Task> {
    return this.http.post<Task>(this.base, task);
  }

  changeStatus(id: string, status: TaskStatus): Observable<Task> {
    return this.http.patch<Task>(`${this.base}/${encodeURIComponent(id)}/status`, { status });
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${encodeURIComponent(id)}`);
  }
}
