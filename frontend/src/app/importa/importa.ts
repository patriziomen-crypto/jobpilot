import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ApiService } from '../api.service';
import { NotificheService } from '../notifiche.service';

/** Importa un'offerta: basta l'URL oppure il testo incollato dall'annuncio. */
@Component({
  selector: 'app-importa',
  imports: [ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatCheckboxModule, MatProgressBarModule],
  templateUrl: './importa.html',
  styleUrl: './importa.scss',
})
export class Importa {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly notifiche = inject(NotificheService);

  readonly inCorso = signal(false);

  readonly form = this.fb.nonNullable.group({
    url: ['', [Validators.pattern(/^https?:\/\/.+/)]],
    text: [''],
    title: [''],
    company: [''],
    location: [''],
    remote: [false],
  });

  get manca(): boolean {
    const v = this.form.getRawValue();
    return !v.url.trim() && !v.text.trim();
  }

  importa(): void {
    if (this.form.invalid || this.manca) return;
    const v = this.form.getRawValue();
    this.inCorso.set(true);
    this.api.importJob({
      url: v.url.trim() || undefined,
      text: v.text.trim() || undefined,
      title: v.title.trim() || undefined,
      company: v.company.trim() || undefined,
      location: v.location.trim() || undefined,
      remote: v.remote || undefined,
    }).subscribe({
      next: app => {
        this.inCorso.set(false);
        this.notifiche.ok(`Offerta "${app.job.title}" salvata`);
        this.router.navigate(['/candidature', app.id]);
      },
      error: err => { this.inCorso.set(false); this.notifiche.errore(err, 'Import non riuscito'); },
    });
  }
}
