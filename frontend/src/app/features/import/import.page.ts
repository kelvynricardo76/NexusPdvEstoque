import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SessionService } from '../../core/auth/session.service';
import { IMPORT_TYPE_LABELS } from '../../core/i18n/labels';
import {
  ImportEntityType,
  ImportJobSummary,
  ImportReport,
  ImportUpload,
  ImportValidation,
  Page,
} from '../../core/models/api.models';
import { ToastService } from '../../core/ui/toast.service';
import { errorMessage } from '../../core/util/api-error';
import { downloadBlob } from '../../core/util/http-params';
import { AlertComponent } from '../../shared/ui/alert/alert.component';
import { BadgeComponent } from '../../shared/ui/badge/badge.component';
import { ButtonComponent } from '../../shared/ui/button/button.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { PageHeaderComponent } from '../../shared/ui/states/states.components';

type Step = 'upload' | 'mapping' | 'preview' | 'done';

const TYPE_PERMISSION: Record<ImportEntityType, string> = {
  PRODUCTS: 'PRODUCT_CREATE',
  CATEGORIES: 'CATEGORY_CREATE',
  STOCK: 'STOCK_ENTRY',
  CUSTOMERS: 'CUSTOMER_CREATE',
  SUPPLIERS: 'SUPPLIER_CREATE',
};

/** Assistente: UPLOAD → MAPEAMENTO → VALIDAÇÃO/PREVIEW → CONFIRMAÇÃO → RELATÓRIO. */
@Component({
  selector: 'nx-import-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, FormsModule, AlertComponent, BadgeComponent, ButtonComponent, IconComponent, PageHeaderComponent],
  templateUrl: './import.page.html',
  styleUrl: './import.page.scss',
})
export class ImportPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly toast = inject(ToastService);
  protected readonly session = inject(SessionService);
  protected readonly typeLabels = IMPORT_TYPE_LABELS;

  protected readonly step = signal<Step>('upload');
  protected readonly type = signal<ImportEntityType>('PRODUCTS');
  protected readonly file = signal<File | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly upload = signal<ImportUpload | null>(null);
  protected readonly mapping = signal<Record<string, number | null>>({});
  protected readonly validation = signal<ImportValidation | null>(null);
  protected readonly skipInvalid = signal(false);
  protected readonly report = signal<ImportReport | null>(null);
  protected readonly history = signal<ImportJobSummary[]>([]);

  protected readonly types = computed(() =>
    (Object.keys(TYPE_PERMISSION) as ImportEntityType[]).filter((type) => {
      if (!this.session.hasPermission(TYPE_PERMISSION[type])) return false;
      return type !== 'SUPPLIERS' || this.session.hasFeature('SUPPLIERS');
    }),
  );

  ngOnInit(): void {
    this.loadHistory();
    const first = this.types()[0];
    if (first) this.type.set(first);
  }

  protected loadHistory(): void {
    this.http.get<Page<ImportJobSummary>>('/api/imports?size=10').subscribe({
      next: (page) => this.history.set(page.content),
      error: () => undefined,
    });
  }

  protected onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file.set(input.files?.[0] ?? null);
    this.error.set(null);
  }

  protected downloadTemplate(): void {
    this.http.get(`/api/imports/templates/${this.type()}`, { responseType: 'blob' }).subscribe({
      next: (blob) => downloadBlob(blob, `modelo-${this.type().toLowerCase()}.csv`),
      error: () => this.toast.error('Não foi possível baixar o modelo.'),
    });
  }

  protected send(): void {
    const file = this.file();
    if (!file) {
      this.error.set('Selecione um arquivo CSV ou XLSX.');
      return;
    }
    const form = new FormData();
    form.append('file', file);
    this.busy.set(true);
    this.error.set(null);
    this.http.post<ImportUpload>(`/api/imports?type=${this.type()}`, form).subscribe({
      next: (upload) => {
        this.busy.set(false);
        this.upload.set(upload);
        const mapping: Record<string, number | null> = {};
        for (const field of upload.fields) mapping[field.key] = upload.suggestedMapping[field.key] ?? null;
        this.mapping.set(mapping);
        this.step.set('mapping');
      },
      error: (error) => {
        this.busy.set(false);
        this.error.set(errorMessage(error));
      },
    });
  }

  protected setMapping(field: string, column: string): void {
    this.mapping.update((current) => ({ ...current, [field]: column === '' ? null : Number(column) }));
  }

  protected validate(): void {
    const upload = this.upload();
    if (!upload) return;
    const mapping = Object.fromEntries(Object.entries(this.mapping()).filter(([, column]) => column !== null));
    this.busy.set(true);
    this.error.set(null);
    this.http.post<ImportValidation>(`/api/imports/${upload.jobId}/validate`, { mapping }).subscribe({
      next: (validation) => {
        this.busy.set(false);
        this.validation.set(validation);
        this.skipInvalid.set(false);
        this.step.set('preview');
      },
      error: (error) => {
        this.busy.set(false);
        this.error.set(errorMessage(error));
      },
    });
  }

  protected confirm(): void {
    const upload = this.upload();
    const validation = this.validation();
    if (!upload || !validation) return;
    if (validation.invalidRows > 0 && !this.skipInvalid()) {
      this.error.set('Confirme que as linhas inválidas serão ignoradas, ou corrija o arquivo e envie novamente.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.http.post<ImportReport>(`/api/imports/${upload.jobId}/confirm`, { skipInvalid: this.skipInvalid() }).subscribe({
      next: (report) => {
        this.busy.set(false);
        this.report.set(report);
        this.step.set('done');
        this.toast.success('Importação concluída.');
        this.loadHistory();
      },
      error: (error) => {
        this.busy.set(false);
        this.error.set(errorMessage(error));
      },
    });
  }

  protected restart(): void {
    this.step.set('upload');
    this.file.set(null);
    this.upload.set(null);
    this.validation.set(null);
    this.report.set(null);
    this.error.set(null);
  }

  protected fieldLabel(key: string): string {
    return this.upload()?.fields.find((field) => field.key === key)?.label ?? key;
  }

  protected previewKeys(): string[] {
    return this.upload()?.fields.map((field) => field.key).filter((key) => this.mapping()[key] !== null) ?? [];
  }
}
