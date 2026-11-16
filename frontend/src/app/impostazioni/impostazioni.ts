import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiService } from '../api.service';
import { NotificheService } from '../notifiche.service';
import { ProviderDto, ProviderPreset, SettingsDto } from '../models';

/**
 * Impostazioni: i fornitori di intelligenza artificiale salvati, con stato (funziona / non funziona / mai provato),
 * interruttore attivo/spento e ordine di prova. Se tutti falliscono l'app usa la modalità base senza AI.
 */
@Component({
  selector: 'app-impostazioni',
  imports: [FormsModule, DatePipe, MatAutocompleteModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatMenuModule, MatProgressBarModule, MatSlideToggleModule, MatTooltipModule],
  templateUrl: './impostazioni.html',
  styleUrl: './impostazioni.scss',
})
export class Impostazioni {
  private readonly api = inject(ApiService);
  private readonly notifiche = inject(NotificheService);

  readonly caricamento = signal(true);
  readonly inCorso = signal(false);
  readonly impostazioni = signal<SettingsDto | null>(null);
  readonly fornitori = signal<ProviderDto[]>([]);
  /** Indici delle schede aperte in modifica. */
  readonly inModifica = signal<Set<number>>(new Set());
  /** Modelli disponibili per fornitore (id → elenco), caricati su richiesta. */
  readonly modelli = signal<Record<string, string[]>>({});
  readonly modificato = signal(false);

  /** Invio automatico delle candidature (senza fermarsi prima di "Invia"). */
  readonly invioAutomatico = signal(false);

  constructor() {
    this.ricarica();
    this.api.getApplySettings().subscribe({ next: s => this.invioAutomatico.set(s.autoSubmit) });
  }

  cambiaInvioAutomatico(valore: boolean): void {
    if (valore && !confirm('Attenzione: l\'invio automatico è vietato dai regolamenti di LinkedIn e Indeed e può portare al blocco del tuo account. Vuoi attivarlo lo stesso?')) {
      this.invioAutomatico.set(false);
      return;
    }
    this.api.updateApplySettings(valore).subscribe({
      next: s => { this.invioAutomatico.set(s.autoSubmit); this.notifiche.ok(s.autoSubmit ? 'Invio automatico attivato' : 'Invio automatico disattivato'); },
      error: err => this.notifiche.errore(err, 'Impostazione non salvata'),
    });
  }

  ricarica(): void {
    this.api.getSettings().subscribe({
      next: s => this.applica(s),
      error: err => { this.notifiche.errore(err, 'Impossibile leggere le impostazioni'); this.caricamento.set(false); },
    });
  }

  private applica(s: SettingsDto): void {
    this.impostazioni.set(s);
    this.fornitori.set(s.providers.map(p => ({ ...p })));
    this.inModifica.set(new Set());
    this.modificato.set(false);
    this.caricamento.set(false);
    this.inCorso.set(false);
  }

  aggiungi(preset: ProviderPreset): void {
    this.fornitori.update(lista => [...lista, {
      id: undefined, name: preset.name, type: preset.type, baseUrl: preset.baseUrl ?? '', apiKey: '',
      apiKeyPresent: false, model: preset.model ?? '', enabled: true, jsonMode: preset.jsonMode,
    }]);
    this.inModifica.update(s => new Set(s).add(this.fornitori().length - 1));
    this.modificato.set(true);
  }

  aggiorna(indice: number, campo: keyof ProviderDto, valore: unknown): void {
    this.fornitori.update(lista => lista.map((p, i) => i === indice ? { ...p, [campo]: valore } : p));
    this.modificato.set(true);
  }

  modifica(indice: number): void {
    this.inModifica.update(s => { const n = new Set(s); n.has(indice) ? n.delete(indice) : n.add(indice); return n; });
  }

  /** Interruttore e frecce salvano subito, senza premere Salva. */
  attiva(indice: number, attivo: boolean): void {
    this.fornitori.update(lista => lista.map((p, i) => i === indice ? { ...p, enabled: attivo } : p));
    this.salva(attivo ? 'Fornitore attivato' : 'Fornitore disattivato');
  }

  sposta(indice: number, delta: number): void {
    const lista = [...this.fornitori()];
    const nuovo = indice + delta;
    if (nuovo < 0 || nuovo >= lista.length) return;
    [lista[indice], lista[nuovo]] = [lista[nuovo], lista[indice]];
    this.fornitori.set(lista);
    this.salva('Ordine aggiornato');
  }

  rimuovi(indice: number): void {
    const p = this.fornitori()[indice];
    if (!confirm(`Rimuovere "${p.name}"? La chiave salvata verrà cancellata.`)) return;
    this.fornitori.update(lista => lista.filter((_, i) => i !== indice));
    this.salva('Fornitore rimosso');
  }

  salva(messaggio = 'Impostazioni salvate'): void {
    const s = this.impostazioni();
    if (!s) return;
    for (const p of this.fornitori()) {
      if (!p.name.trim()) { this.notifiche.errore(new Error('Ogni fornitore deve avere un nome')); return; }
      if (p.type === 'OPENAI_COMPATIBLE' && !(p.baseUrl ?? '').trim()) { this.notifiche.errore(new Error(`${p.name}: manca l'indirizzo del servizio`)); return; }
      if (!(p.model ?? '').trim()) { this.notifiche.errore(new Error(`${p.name}: manca il nome del modello`)); return; }
    }
    this.inCorso.set(true);
    this.api.updateSettings({ ...s, providers: this.fornitori() }).subscribe({
      next: agg => { this.applica(agg); this.notifiche.ok(messaggio); },
      error: err => { this.inCorso.set(false); this.notifiche.errore(err, 'Salvataggio non riuscito'); },
    });
  }

  prova(p: ProviderDto): void {
    if (!p.id || this.modificato()) {
      this.notifiche.errore(new Error('Salva prima le modifiche, poi prova la connessione'));
      return;
    }
    this.inCorso.set(true);
    this.api.testProvider(p.id).subscribe({
      next: r => { this.notifiche.ok(r.message); this.ricarica(); },
      error: err => { this.notifiche.errore(err, 'Prova non riuscita'); this.ricarica(); },
    });
  }

  caricaModelli(p: ProviderDto): void {
    if (!p.id || this.modificato()) {
      this.notifiche.errore(new Error('Salva prima le modifiche, poi chiedi i modelli disponibili'));
      return;
    }
    this.inCorso.set(true);
    this.api.providerModels(p.id).subscribe({
      next: lista => {
        this.modelli.update(m => ({ ...m, [p.id!]: lista }));
        this.inCorso.set(false);
        if (lista.length === 0) this.notifiche.errore(new Error('Il fornitore non ha restituito nessun modello'));
        else this.notifiche.ok(`${lista.length} modelli disponibili: scegli dal campo Modello`);
      },
      error: err => { this.inCorso.set(false); this.notifiche.errore(err, 'Elenco modelli non disponibile'); },
    });
  }

  scegliModello(indice: number, modello: string): void {
    this.aggiorna(indice, 'model', modello);
  }

  presetDi(p: ProviderDto): ProviderPreset | undefined {
    const presets = this.impostazioni()?.presets ?? [];
    return presets.find(x => x.name === p.name) ?? presets.find(x => x.type === p.type && x.baseUrl === p.baseUrl);
  }

  statoTesto(p: ProviderDto): string {
    if (!p.enabled) return 'Disattivato';
    if (!p.apiKeyPresent && this.presetDi(p)?.needsKey !== false) return 'Manca la chiave';
    if (p.lastStatus === 'OK') return 'Funziona';
    if (p.lastStatus === 'ERRORE') return 'Non funziona';
    return 'Mai provato';
  }

  statoClasse(p: ProviderDto): string {
    if (!p.enabled) return 'spento';
    if (!p.apiKeyPresent && this.presetDi(p)?.needsKey !== false) return 'ko';
    if (p.lastStatus === 'OK') return 'ok';
    if (p.lastStatus === 'ERRORE') return 'ko';
    return 'neutro';
  }
}
