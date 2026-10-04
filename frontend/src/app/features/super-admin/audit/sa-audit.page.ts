import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AUDIT_ACTION_LABELS } from '../../../core/i18n/labels';
import { AuditEntry, Page } from '../../../core/models/api.models';
import { errorMessage } from '../../../core/util/api-error';
import { params } from '../../../core/util/http-params';
import { EmptyStateComponent, ErrorStateComponent, PageHeaderComponent, PaginationComponent } from '../../../shared/ui/states/states.components';
import { AuditTableComponent } from '../../audit/audit-table.component';

@Component({
  selector: 'nx-sa-audit-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, AuditTableComponent, EmptyStateComponent, ErrorStateComponent, PageHeaderComponent, PaginationComponent],
  template: `
    <div class="nx-page">
      <nx-page-header title="Auditoria da plataforma" subtitle="Ações de todos os tenants e do Super Admin" />
      <div class="nx-toolbar">
        <select class="nx-control" [ngModel]="action()" (ngModelChange)="action.set($event); reload()" aria-label="Ação">
          <option value="">Todas as ações</option>
          @for (entry of actions; track entry[0]) { <option [value]="entry[0]">{{ entry[1] }}</option> }
        </select>
        <input class="nx-control" type="date" [ngModel]="from()" (ngModelChange)="from.set($event); reload()" aria-label="De" />
        <input class="nx-control" type="date" [ngModel]="to()" (ngModelChange)="to.set($event); reload()" aria-label="Até" />
      </div>
      <div class="nx-table-wrap">
        @if (error(); as message) {
          <nx-error-state [message]="message" (retry)="load()" />
        } @else if (page(); as p) {
          @if (p.content.length) {
            <nx-audit-table [entries]="p.content" />
            <nx-pagination [page]="p.page" [totalPages]="p.totalPages" [totalElements]="p.totalElements" (pageChange)="goTo($event)" />
          } @else {
            <nx-empty-state icon="file-text" title="Nenhum registro" />
          }
        }
      </div>
    </div>
  `,
})
export class SaAuditPage implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly actions = Object.entries(AUDIT_ACTION_LABELS);
  protected readonly action = signal('');
  protected readonly from = signal('');
  protected readonly to = signal('');
  protected readonly pageIndex = signal(0);
  protected readonly page = signal<Page<AuditEntry> | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected reload(): void {
    this.pageIndex.set(0);
    this.load();
  }

  protected goTo(page: number): void {
    this.pageIndex.set(page);
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    this.http
      .get<Page<AuditEntry>>('/api/super-admin/audit', {
        params: params({ action: this.action(), from: this.from(), to: this.to(), page: this.pageIndex(), size: 30 }),
      })
      .subscribe({ next: (page) => this.page.set(page), error: (error) => this.error.set(errorMessage(error)) });
  }
}
