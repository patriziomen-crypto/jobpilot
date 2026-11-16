import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ApiService } from '../api.service';
import { NotificheService } from '../notifiche.service';
import { STATI, StatsDto, labelStato, ApplicationStatus } from '../models';

/** Numeri della ricerca di lavoro: stato, fonti, andamento settimanale, cose da fare. */
@Component({
  selector: 'app-statistiche',
  imports: [RouterLink, DatePipe, MatCardModule, MatIconModule, MatProgressBarModule],
  templateUrl: './statistiche.html',
  styleUrl: './statistiche.scss',
})
export class Statistiche {
  private readonly api = inject(ApiService);
  private readonly notifiche = inject(NotificheService);

  readonly caricamento = signal(true);
  readonly stats = signal<StatsDto | null>(null);
  readonly stati = STATI;
  readonly labelStato = labelStato;

  readonly maxSettimana = computed(() => {
    const s = this.stats();
    if (!s) return 1;
    return Math.max(1, ...s.settimane.map(w => Math.max(w.inviate, w.trovate)));
  });

  constructor() {
    this.api.getStats().subscribe({
      next: s => { this.stats.set(s); this.caricamento.set(false); },
      error: err => { this.notifiche.errore(err, 'Impossibile caricare le statistiche'); this.caricamento.set(false); },
    });
  }

  conteggio(stato: ApplicationStatus): number {
    return this.stats()?.perStato[stato] ?? 0;
  }

  altezza(valore: number): string {
    return `${Math.round((valore / this.maxSettimana()) * 100)}%`;
  }

  etichettaFonte(source: string): string {
    return source === 'LINKEDIN' ? 'LinkedIn' : source === 'INDEED' ? 'Indeed' : 'Altro';
  }
}
