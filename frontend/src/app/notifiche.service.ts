import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

/** Messaggi in basso allo schermo: conferme ed errori del backend in italiano. */
@Injectable({ providedIn: 'root' })
export class NotificheService {
  private readonly snack = inject(MatSnackBar);

  ok(messaggio: string): void {
    this.snack.open(messaggio, 'OK', { duration: 3500 });
  }

  errore(err: unknown, fallback = 'Si è verificato un errore'): void {
    let messaggio = fallback;
    if (err instanceof HttpErrorResponse) {
      messaggio = err.error?.message ?? (err.status === 0 ? 'Backend non raggiungibile: è avviato?' : `${fallback} (HTTP ${err.status})`);
    } else if (err instanceof Error) {
      messaggio = err.message;
    }
    this.snack.open(messaggio, 'Chiudi', { duration: 8000, panelClass: 'errore' });
  }
}
