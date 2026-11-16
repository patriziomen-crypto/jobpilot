import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { PersoneService } from './persone.service';
import { NotificheService } from './notifiche.service';
import { Assistente } from './assistente/assistente';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatButtonModule, MatIconModule, MatMenuModule, MatTooltipModule, Assistente],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  readonly persone = inject(PersoneService);
  private readonly notifiche = inject(NotificheService);

  constructor() {
    this.persone.carica().subscribe({ error: err => this.notifiche.errore(err, 'Impossibile leggere le persone') });
  }

  get personaAttiva() {
    const id = this.persone.attivaId();
    return this.persone.persone().find(p => p.id === id) ?? null;
  }

  nuovaPersona(): void {
    const nome = prompt('Nome e cognome della nuova persona:');
    if (!nome || !nome.trim()) return;
    const titolo = prompt('Titolo professionale (es. "Sviluppatrice Junior Front End"):', '') ?? '';
    const copia = confirm('Copiare competenze, risposte pronte e impostazioni di ricerca dalla persona attuale?');
    this.persone.crea(nome.trim(), titolo.trim(), copia ? this.persone.attivaId() : null).subscribe({
      next: p => { this.notifiche.ok(`Creata ${p.fullName}`); this.persone.seleziona(p.id); },
      error: err => this.notifiche.errore(err, 'Creazione non riuscita'),
    });
  }

  eliminaPersona(): void {
    const p = this.personaAttiva;
    if (!p) return;
    if (!confirm(`Eliminare ${p.fullName} con tutte le sue candidature? L'operazione non si può annullare.`)) return;
    this.persone.elimina(p.id).subscribe({
      next: () => { this.notifiche.ok('Persona eliminata'); this.persone.seleziona(this.persone.persone().find(x => x.id !== p.id)?.id ?? 0); },
      error: err => this.notifiche.errore(err, 'Eliminazione non riuscita'),
    });
  }
}
