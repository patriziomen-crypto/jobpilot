import { Component, ElementRef, effect, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiService } from '../api.service';
import { NotificheService } from '../notifiche.service';
import { PersoneService } from '../persone.service';
import { ChatMessageDto } from '../models';

/** Riga mostrata nel pannello: messaggio più, per l'assistente, chi ha risposto e cosa ha fatto. */
interface Riga extends ChatMessageDto {
  provider?: string;
  done?: string[];
}

/** Assistente in chat, sempre disponibile in basso a destra: usa i fornitori AI attivi nelle Impostazioni. */
@Component({
  selector: 'app-assistente',
  imports: [FormsModule, MatButtonModule, MatIconModule, MatProgressBarModule, MatTooltipModule],
  templateUrl: './assistente.html',
  styleUrl: './assistente.scss',
})
export class Assistente {
  private readonly api = inject(ApiService);
  private readonly notifiche = inject(NotificheService);
  private readonly persone = inject(PersoneService);
  private readonly router = inject(Router);
  private readonly lista = viewChild<ElementRef<HTMLDivElement>>('lista');

  readonly aperto = signal(false);
  readonly caricato = signal(false);
  readonly inCorso = signal(false);
  readonly testo = signal('');
  readonly messaggi = signal<Riga[]>([]);

  readonly suggerimenti = [
    'Quali candidature dovrei seguire adesso?',
    'Come collego Claude?',
    'Come funziona l\'invio assistito?',
    'Avvia la ricerca automatica',
  ];

  constructor() {
    // Cambiando persona la conversazione è un'altra: ricarico la storia
    effect(() => {
      this.persone.attivaId();
      this.caricato.set(false);
      this.messaggi.set([]);
      if (this.aperto()) this.carica();
    });
  }

  apri(): void {
    this.aperto.set(!this.aperto());
    if (this.aperto() && !this.caricato()) this.carica();
  }

  private carica(): void {
    this.api.chatHistory().subscribe({
      next: m => { this.messaggi.set(m); this.caricato.set(true); this.scorri(); },
      error: err => this.notifiche.errore(err, 'Impossibile leggere la conversazione'),
    });
  }

  invia(testo?: string): void {
    const t = (testo ?? this.testo()).trim();
    if (!t || this.inCorso()) return;
    this.testo.set('');
    this.messaggi.update(m => [...m, { role: 'user', content: t }]);
    this.inCorso.set(true);
    this.scorri();
    this.api.chatSend(t).subscribe({
      next: r => {
        this.messaggi.update(m => [...m, { ...r.message, provider: r.provider, done: r.done }]);
        this.inCorso.set(false);
        this.scorri();
        if (r.navigateTo) this.router.navigateByUrl(r.navigateTo);
      },
      error: err => {
        this.inCorso.set(false);
        this.messaggi.update(m => [...m, { role: 'assistant', content: 'Non sono riuscito a rispondere: ' + (err?.error?.message ?? 'errore di rete'), provider: 'errore' }]);
        this.scorri();
      },
    });
  }

  tasto(ev: KeyboardEvent): void {
    if (ev.key === 'Enter' && !ev.shiftKey) {
      ev.preventDefault();
      this.invia();
    }
  }

  nuovaChat(): void {
    this.api.chatReset().subscribe({
      next: () => this.messaggi.set([]),
      error: err => this.notifiche.errore(err, 'Operazione non riuscita'),
    });
  }

  private scorri(): void {
    setTimeout(() => {
      const el = this.lista()?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    }, 30);
  }
}
