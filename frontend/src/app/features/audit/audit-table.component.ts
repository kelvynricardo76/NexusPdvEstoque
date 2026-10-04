import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { AUDIT_ACTION_LABELS, label } from '../../core/i18n/labels';
import { AuditEntry } from '../../core/models/api.models';
import { BadgeComponent } from '../../shared/ui/badge/badge.component';

/** Tabela de trilha de auditoria (reutilizada pelo tenant e pelo Super Admin). */
@Component({
  selector: 'nx-audit-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, BadgeComponent],
  template: `
    <div class="nx-table-scroll">
      <table class="nx-table nx-table--stack">
        <thead>
          <tr><th>Data</th><th>Usuário</th><th>Ação</th><th>Registro</th><th>Detalhes</th><th>IP</th></tr>
        </thead>
        <tbody>
          @for (entry of entries(); track entry.id) {
            <tr>
              <td data-label="Data">{{ entry.createdAt | date: 'dd/MM/yyyy HH:mm:ss' }}</td>
              <td class="nx-stack-main">
                <span class="nx-table__main">{{ entry.actorName || actorLabel(entry.actorType) }}</span>
                <span class="nx-table__sub">{{ actorLabel(entry.actorType) }}</span>
              </td>
              <td><nx-badge [tone]="tone(entry.action)">{{ actionLabel(entry.action) }}</nx-badge></td>
              <td data-label="Registro">{{ entry.entity || '—' }}</td>
              <td data-label="Detalhes" class="details">{{ entry.metadata || '—' }}</td>
              <td data-label="IP" class="text-muted">{{ entry.ip || '—' }}</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: `.details { max-width: 360px; font-family: ui-monospace, monospace; font-size: 11px; color: var(--nx-text-muted); word-break: break-word; }`,
})
export class AuditTableComponent {
  readonly entries = input.required<AuditEntry[]>();

  protected actionLabel(action: string): string {
    return label(AUDIT_ACTION_LABELS, action);
  }

  protected actorLabel(type: string): string {
    return { PLATFORM_ADMIN: 'Nexus (Super Admin)', TENANT_USER: 'Usuário', SYSTEM: 'Sistema', ANONYMOUS: 'Anônimo' }[type] ?? type;
  }

  protected tone(action: string): 'danger' | 'warning' | 'success' | 'info' | 'neutral' {
    if (['LOGIN_FAILED', 'SALE_CANCEL', 'TENANT_SUSPEND', 'TENANT_CANCEL', 'DISABLE', 'DELETE'].includes(action)) return 'danger';
    if (['PERMISSION_CHANGE', 'ROLE_CHANGE', 'PLAN_CHANGE', 'STOCK_ADJUST', 'FEATURE_OVERRIDE', 'LIMIT_OVERRIDE'].includes(action)) return 'warning';
    if (['SALE', 'CREATE', 'TENANT_CREATE', 'IMPORT'].includes(action)) return 'success';
    if (action === 'LOGIN' || action === 'LOGOUT') return 'info';
    return 'neutral';
  }
}
