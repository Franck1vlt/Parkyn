export interface PageMeta {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Page<T> {
  content: T[];
  page: PageMeta;
}
