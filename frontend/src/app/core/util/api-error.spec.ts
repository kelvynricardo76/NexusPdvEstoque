import { HttpErrorResponse } from '@angular/common/http';
import { errorMessage, toApiError } from './api-error';

describe('api-error', () => {
  it('extrai o erro padronizado da API', () => {
    const error = new HttpErrorResponse({
      status: 403,
      error: { code: 'FEATURE_NOT_AVAILABLE', message: 'Este recurso não está disponível no seu plano.' },
    });
    expect(toApiError(error).code).toBe('FEATURE_NOT_AVAILABLE');
    expect(errorMessage(error)).toBe('Este recurso não está disponível no seu plano.');
  });

  it('detalha erros de validação por campo', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: { code: 'VALIDATION_ERROR', message: 'Dados inválidos.', fields: [{ field: 'name', message: 'obrigatório' }] },
    });
    expect(errorMessage(error)).toBe('name: obrigatório');
  });

  it('trata falha de rede', () => {
    expect(toApiError(new HttpErrorResponse({ status: 0 })).code).toBe('NETWORK_ERROR');
  });
});
