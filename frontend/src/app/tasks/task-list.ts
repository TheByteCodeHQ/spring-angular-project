import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { AuthService } from '../core/auth.service';
import { Page, TASK_STATUSES, Task, TaskStatus } from '../core/models';
import { describeError } from '../core/problem';
import { TaskService } from '../core/task.service';

const PAGE_SIZE = 10;

@Component({
  selector: 'app-task-list',
  imports: [DatePipe],
  templateUrl: './task-list.html',
  styleUrl: './task-list.css',
})
export class TaskList implements OnInit {
  private readonly tasks = inject(TaskService);
  protected readonly auth = inject(AuthService);

  protected readonly statuses = TASK_STATUSES;
  protected readonly result = signal<Page<Task> | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal('');

  protected readonly statusFilter = signal<TaskStatus | ''>('');
  protected readonly search = signal('');
  protected readonly newTitle = signal('');
  protected readonly newDue = signal('');
  protected readonly confirmingDelete = signal<string | null>(null);

  protected readonly page = computed(() => this.result()?.page ?? 0);
  protected readonly totalPages = computed(() => this.result()?.totalPages ?? 0);
  protected readonly items = computed(() => this.result()?.content ?? []);
  protected readonly today = new Date().toISOString().slice(0, 10);

  ngOnInit(): void {
    this.load(0);
  }

  protected load(page: number): void {
    this.loading.set(true);
    this.tasks
      .list({ status: this.statusFilter(), q: this.search(), page, size: PAGE_SIZE })
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.error.set('');
          this.loading.set(false);
        },
        error: (err: unknown) => this.fail(err),
      });
  }

  protected onFilter(event: Event): void {
    event.preventDefault();
    this.load(0);
  }

  protected onStatusFilter(event: Event): void {
    this.statusFilter.set((event.target as HTMLSelectElement).value as TaskStatus | '');
    this.load(0);
  }

  protected onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  protected onNewTitle(event: Event): void {
    this.newTitle.set((event.target as HTMLInputElement).value);
  }

  protected onNewDue(event: Event): void {
    this.newDue.set((event.target as HTMLInputElement).value);
  }

  protected add(event: Event): void {
    event.preventDefault();
    const title = this.newTitle().trim();
    if (!title) {
      return;
    }
    const due = this.newDue();
    // Clear at once rather than when the server answers: anything typed for the next task while this request
    // is in flight must not be wiped out by the reply.
    this.newTitle.set('');
    this.newDue.set('');
    this.tasks.create({ title, dueDate: due || null }).subscribe({
      next: () => this.load(0),
      error: (err: unknown) => {
        if (!this.newTitle()) {   // give the text back, unless the user has already started something new
          this.newTitle.set(title);
          this.newDue.set(due);
        }
        this.fail(err);
      },
    });
  }

  protected changeStatus(task: Task, event: Event): void {
    const status = (event.target as HTMLSelectElement).value as TaskStatus;
    this.tasks.changeStatus(task.id, status).subscribe({
      next: () => this.load(this.page()),
      error: (err: unknown) => this.fail(err),
    });
  }

  protected askDelete(task: Task): void {
    this.confirmingDelete.set(task.id);
  }

  protected cancelDelete(): void {
    this.confirmingDelete.set(null);
  }

  protected confirmDelete(task: Task): void {
    this.tasks.remove(task.id).subscribe({
      next: () => {
        this.confirmingDelete.set(null);
        // stay on the page unless that deleted its last row
        this.load(this.items().length === 1 && this.page() > 0 ? this.page() - 1 : this.page());
      },
      error: (err: unknown) => this.fail(err),
    });
  }

  protected isOverdue(task: Task): boolean {
    return !!task.dueDate && task.status !== 'DONE' && task.dueDate < this.today;
  }

  protected label(status: TaskStatus): string {
    return status === 'IN_PROGRESS' ? 'In progress' : status === 'TODO' ? 'To do' : 'Done';
  }

  private fail(err: unknown): void {
    this.error.set(describeError(err));
    this.loading.set(false);
  }
}
