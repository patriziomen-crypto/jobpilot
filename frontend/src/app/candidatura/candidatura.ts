import { Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiService } from '../api.service';
import { NotificheService } from '../notifiche.service';
import { ApplicationDto, ApplicationStatus, ApplyStatusDto, ProfileDto, STATI, classePunteggio } from '../models';

/** Dettaglio di una candidatura: analisi AI, CV e lettera, candidatura guidata, note. */
@Component({
  selector: 'app-candidatura',
  imports: [RouterLink, FormsModule, DatePipe, MatButtonModule, MatCardModule, MatChipsModule, MatDividerModule,
    MatExpansionModule, MatFormFieldModule, MatIconModule, MatInputModule, MatProgressBarModule, MatSelectModule,
    MatTooltipModule],
  templateUrl: './candidatura.html',
  styleUrl: './candidatura.scss',
})
export class Candidatura {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly notifiche = inject(NotificheService);

  /** Arriva dalla rotta /candidature/:id grazie a withComponentInputBinding. */
  readonly id = input.required<string>();

  readonly stati = STATI;
  readonly classePunteggio = classePunteggio;
  readonly candidatura = signal<ApplicationDto | null>(null);
  readonly profilo = signal<ProfileDto | null>(null);
  readonly caricamento = signal(true);
  readonly operazione = signal<'' | 'analisi' | 'documenti' | 'salvataggio'>('');
  readonly note = signal('');
  readonly followUp = signal('');

  readonly analisi = computed(() => this.candidatura()?.analysis ?? null);
  readonly haDocumenti = computed(() => !!this.candidatura()?.cvPath || !!this.candidatura()?.coverLetter);

  /** Stato dell'invio assistito (finestra del browser pilotata dall'app). */
  readonly invio = signal<ApplyStatusDto | null>(null);
  readonly invioAttivo = computed(() => {
    const s = this.invio();
    const id = this.candidatura()?.id;
    return !!s && s.applicationId === id && (s.running || s.windowOpen);
  });
  private timerInvio: ReturnType<typeof setInterval> | null = null;
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    effect(() => {
      const id = Number(this.id());
      if (!Number.isNaN(id)) this.carica(id);
    });
    this.api.getProfile().subscribe({ next: p => this.profilo.set(p) });
    this.aggiornaInvio();
    this.timerInvio = setInterval(() => this.aggiornaInvio(), 2500);
    this.destroyRef.onDestroy(() => { if (this.timerInvio) clearInterval(this.timerInvio); });
  }

  private aggiornaInvio(): void {
    this.api.applyStatus().subscribe({
      next: s => {
        const prima = this.invio();
        this.invio.set(s);
        // quando l'app ha inviato da sola, ricarico la candidatura per vedere lo stato INVIATA
        if (prima?.running && !s.running && s.outcome === 'SUBMITTED' && s.applicationId === this.candidatura()?.id) {
          this.carica(s.applicationId!);
        }
      },
    });
  }

  compilaCandidatura(): void {
    const c = this.candidatura();
    if (!c) return;
    this.api.applyStart(c.id).subscribe({
      next: s => { this.invio.set(s); this.notifiche.ok('Apro il browser dell\'app e compilo il modulo…'); },
      error: err => this.notifiche.errore(err, 'Invio assistito non avviato'),
    });
  }

  hoInviato(): void {
    this.api.applyConfirm().subscribe({
      next: s => { this.invio.set(s); this.notifiche.ok('Candidatura segnata come inviata'); const id = this.candidatura()?.id; if (id) this.carica(id); },
      error: err => this.notifiche.errore(err, 'Operazione non riuscita'),
    });
  }

  annullaInvio(): void {
    this.api.applyCancel().subscribe({
      next: s => this.invio.set(s),
      error: err => this.notifiche.errore(err, 'Operazione non riuscita'),
    });
  }

  esitoTesto(): string {
    switch (this.invio()?.outcome) {
      case 'SUBMITTED': return 'Candidatura inviata dall\'app';
      case 'READY_FOR_REVIEW': return 'Modulo compilato: controlla nella finestra e premi Invia, poi "Ho inviato" qui';
      case 'NEEDS_INPUT': return 'Mancano alcune risposte: completale nella finestra, invia, poi premi "Ho inviato"';
      case 'EXTERNAL': return 'L\'annuncio rimanda al sito dell\'azienda: completa la candidatura nella finestra, poi premi "Ho inviato"';
      case 'LOGIN_REQUIRED': return 'Serve prima l\'accesso al sito: usa "Accedi a LinkedIn / Indeed" nella pagina Ricerca';
      case 'FAILED': return 'Non sono riuscito a compilare: candidati a mano con "Apri annuncio"';
      default: return '';
    }
  }

  private carica(id: number): void {
    this.caricamento.set(true);
    this.api.getApplication(id).subscribe({
      next: c => this.imposta(c),
      error: err => { this.notifiche.errore(err, 'Candidatura non trovata'); this.router.navigate(['/']); },
    });
  }

  private imposta(c: ApplicationDto): void {
    this.candidatura.set(c);
    this.note.set(c.notes ?? '');
    this.followUp.set(c.followUpAt ?? '');
    this.caricamento.set(false);
    this.operazione.set('');
  }

  analizza(): void {
    const c = this.candidatura();
    if (!c) return;
    this.operazione.set('analisi');
    this.api.analyze(c.id).subscribe({
      next: agg => { this.imposta(agg); this.notifiche.ok(`Analisi completata: ${agg.analysis?.score}/100`); },
      error: err => { this.operazione.set(''); this.notifiche.errore(err, 'Analisi non riuscita'); },
    });
  }

  genera(cv = true, lettera = true): void {
    const c = this.candidatura();
    if (!c) return;
    this.operazione.set('documenti');
    this.api.generate(c.id, cv, lettera).subscribe({
      next: agg => { this.imposta(agg); this.notifiche.ok('Documenti generati e salvati nella cartella della candidatura'); },
      error: err => { this.operazione.set(''); this.notifiche.errore(err, 'Generazione non riuscita'); },
    });
  }

  cambiaStato(stato: ApplicationStatus): void {
    const c = this.candidatura();
    if (!c || c.status === stato) return;
    this.api.changeStatus(c.id, stato).subscribe({
      next: agg => { this.imposta(agg); this.notifiche.ok('Stato aggiornato'); },
      error: err => this.notifiche.errore(err, 'Cambio di stato non riuscito'),
    });
  }

  segnaInviata(): void {
    this.cambiaStato('INVIATA');
  }

  salvaNote(): void {
    const c = this.candidatura();
    if (!c) return;
    this.operazione.set('salvataggio');
    this.api.updateNotes(c.id, this.note().trim() || null, this.followUp() || null).subscribe({
      next: agg => { this.imposta(agg); this.notifiche.ok('Note salvate'); },
      error: err => { this.operazione.set(''); this.notifiche.errore(err, 'Salvataggio non riuscito'); },
    });
  }

  apriAnnuncio(): void {
    const url = this.candidatura()?.job.url;
    if (url) window.open(url, '_blank', 'noopener');
  }

  apriCartella(): void {
    const c = this.candidatura();
    if (!c) return;
    this.api.openFolder(c.id).subscribe({
      error: err => this.notifiche.errore(err, 'Impossibile aprire la cartella'),
    });
  }

  async copia(testo: string | undefined, cosa: string): Promise<void> {
    if (!testo) return;
    try {
      await navigator.clipboard.writeText(testo);
      this.notifiche.ok(`${cosa} copiata negli appunti`);
    } catch {
      this.notifiche.errore(new Error('Copia non riuscita: seleziona e copia il testo a mano'));
    }
  }

  elimina(): void {
    const c = this.candidatura();
    if (!c || !confirm(`Eliminare la candidatura "${c.job.title}"? L'operazione non si può annullare.`)) return;
    this.api.deleteApplication(c.id).subscribe({
      next: () => { this.notifiche.ok('Candidatura eliminata'); this.router.navigate(['/']); },
      error: err => this.notifiche.errore(err, 'Eliminazione non riuscita'),
    });
  }
}
