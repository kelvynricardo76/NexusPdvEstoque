import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../models/api.models';

/** Extrai o erro padronizado da API (ou um genérico quando a resposta não é JSON). */
export function toApiError(error: unknown): ApiError {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as Partial<ApiError> | null;
    if (body && typeof body === 'object' && typeof body.code === 'string') {
      return { code: body.code, message: body.message ?? 'Erro inesperado.', fields: body.fields, traceId: body.traceId };
    }
    if (error.status === 0) {
      return { code: 'NETWORK_ERROR', message: 'Não foi possível conectar ao servidor.' };
    }
  }
  return { code: 'UNKNOWN', message: 'Ocorreu um erro inesperado.' };
}

export function errorMessage(error: unknown): string {
  const apiError = toApiError(error);
  if (apiError.code === 'VALIDATION_ERROR' && apiError.fields?.length) {
    return apiError.fields.map((field) => `${field.field}: ${field.message}`).join(' • ');
  }
  return apiError.message;
}
