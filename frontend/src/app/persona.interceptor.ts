import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { PersoneService } from './persone.service';

/**
 * Aggiunge a ogni chiamata al backend:
 * - X-Requested-With: richiesto dal backend per tutte le modifiche (protezione CSRF);
 * - X-Profile-Id: la persona selezionata.
 */
export const personaInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api')) return next(req);
  const headers: Record<string, string> = { 'X-Requested-With': 'JobPilot' };
  const id = inject(PersoneService).idCorrente;
  if (id !== null) headers['X-Profile-Id'] = String(id);
  return next(req.clone({ setHeaders: headers }));
};
