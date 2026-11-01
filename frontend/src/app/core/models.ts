export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';

export const TASK_STATUSES: readonly TaskStatus[] = ['TODO', 'IN_PROGRESS', 'DONE'];

export interface Task {
  id: string;
  title: string;
  description: string | null;
  status: TaskStatus;
  dueDate: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface TaskQuery {
  status: TaskStatus | '';
  q: string;
  page: number;
  size: number;
}

export interface NewTask {
  title: string;
  dueDate?: string | null;
}

/** RFC 9457 problem document, as returned by the API for every error. */
export interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
  requestId?: string;
  errors?: { field: string; message: string }[];
}
