import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DatePipe } from '@angular/common';
import { ApiService } from '../api.service';
import { NotificheService } from '../notifiche.service';
import { ApplicationDto, ApplicationStatus, JobSource, STATI, classePunteggio } from '../models';

/** Dashboard Kanban: una colonna per stato, trascina le schede per cambiare stato. */
@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, DragDropModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatProgressBarModule, MatTooltipModule, DatePipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly api = inject(ApiService);
  private readonly notifiche = inject(NotificheService);

  readonly stati = STATI;
  readonly caricamento = signal(true);
  readonly candidature = signal<ApplicationDto[]>([]);
  readonly ricerca = signal('');
  readonly fonte = signal<JobSource | ''>('');
  readonly classePunteggio = classePunteggio;
  readonly oggi = new Date().toISOString().slice(0, 10);

  readonly filtrate = computed(() => {
    const q = this.ricerca().toLowerCase().trim();
    const fonte = this.fonte();
    return this.candidature().filter(c =>
      (!fonte || c.job.source === fonte) &&
      (!q || `${c.job.title} ${c.job.company ?? ''} ${c.job.location ?? ''}`.toLowerCase().includes(q)));
  });

  readonly perStato = computed(() => {
    const mappa = new Map<ApplicationStatus, ApplicationDto[]>();
    for (const s of STATI) mappa.set(s.value, []);
    for (const c of this.filtrate()) mappa.get(c.status)?.push(c);
    return mappa;
  });

  readonly daSeguire = computed(() =>
    this.candidature().filter(c => c.status === 'INVIATA' && c.followUpAt && c.followUpAt <= this.oggi).length);

  readonly idColonne = STATI.map(s => 'col-' + s.value);

  constructor() {
    this.ricarica();
  }

  ricarica(): void {
    this.caricamento.set(true);
    this.api.listApplications().subscribe({
      next: lista => { this.candidature.set(lista); this.caricamento.set(false); },
      error: err => { this.notifiche.errore(err, 'Impossibile caricare le candidature'); this.caricamento.set(false); },
    });
  }

  colonna(stato: ApplicationStatus): ApplicationDto[] {
    return this.perStato().get(stato) ?? [];
  }

  trascinata(evento: CdkDragDrop<ApplicationStatus>): void {
    const candidatura = evento.item.data as ApplicationDto;
    const nuovoStato = evento.container.data;
    if (nuovoStato === candidatura.status) return;

    const precedente = candidatura.status;
    this.candidature.update(lista => lista.map(c => c.id === candidatura.id ? { ...c, status: nuovoStato } : c));
    this.api.changeStatus(candidatura.id, nuovoStato).subscribe({
      next: aggiornata => this.candidature.update(lista => lista.map(c => c.id === aggiornata.id ? aggiornata : c)),
      error: err => {
        this.candidature.update(lista => lista.map(c => c.id === candidatura.id ? { ...c, status: precedente } : c));
        this.notifiche.errore(err, 'Cambio di stato non riuscito');
      },
    });
  }

  scaduta(c: ApplicationDto): boolean {
    return c.status === 'INVIATA' && !!c.followUpAt && c.followUpAt <= this.oggi;
  }
}
