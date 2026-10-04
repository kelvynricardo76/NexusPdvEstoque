import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { SessionService } from '../auth/session.service';
import { toApiError } from '../util/api-error';

/**
 * Reage a erros globais: sessão expirada (401) leva ao login; tenant suspenso ou assinatura
 * inativa leva à tela de bloqueio. Os demais erros seguem para a tela que fez a chamada.
 */
export const apiErrorInterceptor: HttpInterceptorFn = (request, next) => {
  const router = inject(Router);
  const session = inject(SessionService);
  const isAuthEndpoint = request.url.includes('/auth/');
  const isPlatform = request.url.startsWith('/api/super-admin');

  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && !isAuthEndpoint) {
        if (error.status === 401) {
          if (isPlatform) {
            session.clearPlatform();
            void router.navigate(['/super-admin/login']);
          } else {
            session.clearTenant();
            void router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
          }
        } else if (error.status === 403) {
          const code = toApiError(error).code;
          if (code === 'TENANT_SUSPENDED' || code === 'SUBSCRIPTION_INACTIVE') {
            void router.navigate(['/bloqueado']);
          }
        }
      }
      return throwError(() => error);
    }),
  );
};
