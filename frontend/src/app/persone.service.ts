import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { ProfileSummary } from './models';

const CHIAVE = 'jobpilot.profileId';

/** Le persone gestite dall'app e quella attualmente selezionata (ricordata nel browser). */
@Injectable({ providedIn: 'root' })
export class PersoneService {
  private readonly http = inject(HttpClient);

  readonly persone = signal<ProfileSummary[]>([]);
  readonly attivaId = signal<number | null>(leggiSalvata());

  /** Id da mandare al backend nell'header X-Profile-Id (null = profilo predefinito). */
  get idCorrente(): number | null {
    return this.attivaId();
  }

  carica(): Observable<ProfileSummary[]> {
    return this.http.get<ProfileSummary[]>('/api/profiles').pipe(tap(lista => {
      this.persone.set(lista);
      const corrente = this.attivaId();
      if (lista.length > 0 && (corrente === null || !lista.some(p => p.id === corrente))) {
        this.seleziona(lista[0].id, false);
      }
    }));
  }

  /** Cambia persona: tutte le pagine si ricaricano con i dati di quella persona. */
  seleziona(id: number, ricarica = true): void {
    this.attivaId.set(id);
    try { localStorage.setItem(CHIAVE, String(id)); } catch { /* storage non disponibile */ }
    if (ricarica) window.location.reload();
  }

  crea(fullName: string, headline: string, copyFromId: number | null): Observable<ProfileSummary> {
    return this.http.post<ProfileSummary>('/api/profiles', { fullName, headline, copyFromId });
  }

  elimina(id: number): Observable<void> {
    return this.http.delete<void>(`/api/profiles/${id}`);
  }
}

function leggiSalvata(): number | null {
  try {
    const v = localStorage.getItem(CHIAVE);
    return v ? Number(v) : null;
  } catch {
    return null;
  }
}
