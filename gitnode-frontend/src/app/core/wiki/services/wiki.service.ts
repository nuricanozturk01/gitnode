import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { WikiPage, WikiPageSlice, WikiPageSummary, WikiRevision } from '../../../domain/wiki/models/wiki.model';

@Injectable({ providedIn: 'root' })
export class WikiService {
  private readonly http = inject(HttpClient);

  private base(owner: string, repo: string): string {
    return `${environment.apiUrl}/api/repos/${owner}/${repo}/wiki/pages`;
  }

  list(owner: string, repo: string, page: number): Promise<WikiPageSlice<WikiPageSummary>> {
    const params = new HttpParams().set('page', String(page));
    return firstValueFrom(this.http.get<WikiPageSlice<WikiPageSummary>>(this.base(owner, repo), { params }));
  }

  get(owner: string, repo: string, slug: string, rev?: string): Promise<WikiPage> {
    let params = new HttpParams();
    if (rev) params = params.set('rev', rev);
    return firstValueFrom(this.http.get<WikiPage>(`${this.base(owner, repo)}/${encodeURIComponent(slug)}`, { params }));
  }

  history(owner: string, repo: string, slug: string, page: number): Promise<WikiPageSlice<WikiRevision>> {
    const params = new HttpParams().set('page', String(page));
    return firstValueFrom(
      this.http.get<WikiPageSlice<WikiRevision>>(`${this.base(owner, repo)}/${encodeURIComponent(slug)}/history`, {
        params,
      }),
    );
  }

  save(
    owner: string,
    repo: string,
    slug: string,
    form: { content: string; message?: string; baseSha: string | null },
  ): Promise<WikiPage> {
    return firstValueFrom(this.http.put<WikiPage>(`${this.base(owner, repo)}/${encodeURIComponent(slug)}`, form));
  }

  delete(owner: string, repo: string, slug: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(owner, repo)}/${encodeURIComponent(slug)}`));
  }
}
