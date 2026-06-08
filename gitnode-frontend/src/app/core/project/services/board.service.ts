import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { BoardInfo, BoardForm, BoardColumnForm } from '../../../domain/project/models/board-info.model';

@Injectable({ providedIn: 'root' })
export class BoardService {
  private readonly http = inject(HttpClient);

  private base(owner: string, projectCode: string): string {
    return `${environment.apiUrl}/api/projects/${owner}/${projectCode}/boards`;
  }

  getAllBoards(owner: string, projectCode: string): Promise<BoardInfo[]> {
    return firstValueFrom(this.http.get<BoardInfo[]>(this.base(owner, projectCode)));
  }

  getBoard(owner: string, projectCode: string, boardId: string): Promise<BoardInfo> {
    return firstValueFrom(this.http.get<BoardInfo>(`${this.base(owner, projectCode)}/${boardId}`));
  }

  createBoard(owner: string, projectCode: string, form: BoardForm): Promise<BoardInfo> {
    return firstValueFrom(this.http.post<BoardInfo>(this.base(owner, projectCode), form));
  }

  updateBoard(
    owner: string,
    projectCode: string,
    boardId: string,
    form: { name?: string; position?: number },
  ): Promise<BoardInfo> {
    return firstValueFrom(this.http.patch<BoardInfo>(`${this.base(owner, projectCode)}/${boardId}`, form));
  }

  deleteBoard(owner: string, projectCode: string, boardId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(owner, projectCode)}/${boardId}`));
  }

  createColumn(
    owner: string,
    projectCode: string,
    boardId: string,
    form: BoardColumnForm,
  ): Promise<import('../../../domain/project/models/board-info.model').BoardColumnInfo> {
    return firstValueFrom(
      this.http.post<import('../../../domain/project/models/board-info.model').BoardColumnInfo>(
        `${this.base(owner, projectCode)}/${boardId}/columns`,
        form,
      ),
    );
  }

  updateColumn(
    owner: string,
    projectCode: string,
    boardId: string,
    columnId: string,
    form: { name?: string; position?: number; color?: string },
  ): Promise<import('../../../domain/project/models/board-info.model').BoardColumnInfo> {
    return firstValueFrom(
      this.http.patch<import('../../../domain/project/models/board-info.model').BoardColumnInfo>(
        `${this.base(owner, projectCode)}/${boardId}/columns/${columnId}`,
        form,
      ),
    );
  }

  deleteColumn(owner: string, projectCode: string, boardId: string, columnId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${this.base(owner, projectCode)}/${boardId}/columns/${columnId}`));
  }
}
