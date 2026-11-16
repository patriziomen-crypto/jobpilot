import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiService } from '../api.service';
import { NotificheService } from '../notifiche.service';
import { BrowserSite, BrowserStatusDto, SearchSettingsDto, SearchStatusDto, SiteSessionDto } from '../models';

/** Ricerca automatica: l'app va su LinkedIn e Indeed, importa i nuovi annunci e li fa analizzare. */
@Component({
  selector: 'app-ricerca',
  imports: [FormsModule, RouterLink, DatePipe, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatProgressBarModule, MatSlideToggleModule, MatTooltipModule],
  templateUrl: './ricerca.html',
  styleUrl: './ricerca.scss',
})
export class Ricerca {
  private readonly api = inject(ApiService);
  private readonly notifiche = inject(NotificheService);
  private readonly destroyRef = inject(DestroyRef);

  readonly caricamento = signal(true);
  readonly salvataggio = signal(false);
  readonly impostazioni = signal<SearchSettingsDto | null>(null);
  readonly paroleChiave = signal('');
  readonly stato = signal<SearchStatusDto | null>(null);
  readonly browser = signal<BrowserStatusDto | null>(null);
  readonly mostraLogAccessi = signal(false);

  /** I due siti mostrati nella sezione "Account collegati". */
  readonly siti: { key: BrowserSite; nome: string; nota: string }[] = [
    { key: 'linkedin', nome: 'LinkedIn', nota: 'Serve per l\'invio assistito (Candidatura semplice). La ricerca funziona anche senza.' },
    { key: 'indeed', nome: 'Indeed', nota: 'Serve per la ricerca (senza account Indeed mostra pochi annunci) e per l\'invio assistito.' },
  ];

  private timer: ReturnType<typeof setInterval> | null = null;

  constructor() {
    this.api.getSearchSettings().subscribe({
      next: s => { this.impostazioni.set(s); this.paroleChiave.set(s.keywords.join('\n')); this.caricamento.set(false); },
      error: err => { this.notifiche.errore(err, 'Impossibile leggere le impostazioni di ricerca'); this.caricamento.set(false); },
    });
    this.aggiornaStato();
    this.timer = setInterval(() => this.aggiornaStato(), 2500);
    this.destroyRef.onDestroy(() => { if (this.timer) clearInterval(this.timer); });
  }

  private aggiornaStato(): void {
    this.api.getSearchStatus().subscribe({ next: s => this.stato.set(s) });
    this.api.browserStatus().subscribe({ next: b => this.browser.set(b) });
  }

  sessione(site: BrowserSite): SiteSessionDto | null {
    const b = this.browser();
    return b ? b[site] : null;
  }

  statoTesto(s: SiteSessionDto | null): string {
    if (!s || !s.known) return 'Da verificare';
    return s.loggedIn ? 'Connesso' : 'Non connesso';
  }

  statoClasse(s: SiteSessionDto | null): string {
    if (!s || !s.known) return 'neutro';
    return s.loggedIn ? 'ok' : 'ko';
  }

  private daSalvare(): SearchSettingsDto | null {
    const s = this.impostazioni();
    if (!s) return null;
    return {
      ...s,
      keywords: this.paroleChiave().split(/\r?\n|;/).map(k => k.trim()).filter(k => k.length > 0),
    };
  }

  salva(): void {
    const s = this.daSalvare();
    if (!s) return;
    this.salvataggio.set(true);
    this.api.updateSearchSettings(s).subscribe({
      next: agg => { this.impostazioni.set(agg); this.paroleChiave.set(agg.keywords.join('\n')); this.salvataggio.set(false); this.notifiche.ok('Impostazioni di ricerca salvate'); },
      error: err => { this.salvataggio.set(false); this.notifiche.errore(err, 'Salvataggio non riuscito'); },
    });
  }

  cerca(): void {
    const s = this.daSalvare();
    if (!s) return;
    this.salvataggio.set(true);
    this.api.updateSearchSettings(s).subscribe({
      next: agg => {
        this.impostazioni.set(agg);
        this.api.runSearch().subscribe({
          next: st => { this.stato.set(st); this.salvataggio.set(false); this.notifiche.ok('Ricerca avviata'); },
          error: err => { this.salvataggio.set(false); this.notifiche.errore(err, 'Impossibile avviare la ricerca'); },
        });
      },
      error: err => { this.salvataggio.set(false); this.notifiche.errore(err, 'Salvataggio non riuscito'); },
    });
  }

  ferma(): void {
    this.api.stopSearch().subscribe({ next: st => this.stato.set(st), error: err => this.notifiche.errore(err) });
  }

  accedi(site: BrowserSite): void {
    const nome = this.siti.find(s => s.key === site)?.nome ?? site;
    this.api.browserLogin(site).subscribe({
      next: b => {
        this.browser.set(b);
        this.notifiche.ok(b.loginWindowOpen
          ? `Si apre ${nome} nella finestra di Edge: accedi con il tuo account, poi chiudi la finestra`
          : `Finestra di Edge in apertura su ${nome}`);
      },
      error: err => this.notifiche.errore(err, 'Impossibile aprire la finestra di accesso'),
    });
  }

  disconnetti(site: BrowserSite): void {
    const nome = this.siti.find(s => s.key === site)?.nome ?? site;
    this.api.browserLogout(site).subscribe({
      next: b => { this.browser.set(b); this.notifiche.ok(`Disconnessione da ${nome} in corso (qualche secondo)`); },
      error: err => this.notifiche.errore(err, 'Disconnessione non riuscita'),
    });
  }

  verifica(): void {
    this.api.browserCheck().subscribe({
      next: b => { this.browser.set(b); this.notifiche.ok('Verifica degli accessi in corso (qualche secondo)'); },
      error: err => this.notifiche.errore(err, 'Verifica non riuscita'),
    });
  }

  aggiorna<K extends keyof SearchSettingsDto>(campo: K, valore: SearchSettingsDto[K]): void {
    const s = this.impostazioni();
    if (s) this.impostazioni.set({ ...s, [campo]: valore });
  }
}
