import { Component, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiService } from '../api.service';
import { NotificheService } from '../notifiche.service';
import { ProfileDto } from '../models';

/** Modifica del profilo master: dati, esperienze, formazione, competenze e risposte pronte. */
@Component({
  selector: 'app-profilo',
  imports: [ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatProgressBarModule, MatTabsModule, MatTooltipModule],
  templateUrl: './profilo.html',
  styleUrl: './profilo.scss',
})
export class Profilo {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ApiService);
  private readonly notifiche = inject(NotificheService);

  readonly caricamento = signal(true);
  readonly salvataggio = signal(false);

  readonly form = this.fb.nonNullable.group({
    id: [null as number | null],
    fullName: ['', Validators.required],
    headline: ['', Validators.required],
    location: [''],
    phone: [''],
    email: ['', Validators.email],
    linkedinUrl: [''],
    githubUrl: [''],
    summary: [''],
    languages: [''],
    extraInfo: [''],
    experiences: this.fb.array<FormGroup>([]),
    educations: this.fb.array<FormGroup>([]),
    skills: this.fb.array<FormGroup>([]),
    standardAnswers: this.fb.array<FormGroup>([]),
  });

  get experiences(): FormArray<FormGroup> { return this.form.controls.experiences; }
  get educations(): FormArray<FormGroup> { return this.form.controls.educations; }
  get skills(): FormArray<FormGroup> { return this.form.controls.skills; }
  get standardAnswers(): FormArray<FormGroup> { return this.form.controls.standardAnswers; }

  constructor() {
    this.api.getProfile().subscribe({
      next: p => { this.popola(p); this.caricamento.set(false); },
      error: err => { this.notifiche.errore(err, 'Impossibile caricare il profilo'); this.caricamento.set(false); },
    });
  }

  private popola(p: ProfileDto): void {
    this.form.patchValue({
      id: p.id ?? null, fullName: p.fullName, headline: p.headline, location: p.location ?? '', phone: p.phone ?? '',
      email: p.email ?? '', linkedinUrl: p.linkedinUrl ?? '', githubUrl: p.githubUrl ?? '', summary: p.summary ?? '',
      languages: p.languages ?? '', extraInfo: p.extraInfo ?? '',
    });
    this.experiences.clear();
    p.experiences.forEach(e => this.experiences.push(this.nuovaEsperienza(e)));
    this.educations.clear();
    p.educations.forEach(e => this.educations.push(this.nuovaFormazione(e)));
    this.skills.clear();
    p.skills.forEach(s => this.skills.push(this.nuovaCompetenza(s)));
    this.standardAnswers.clear();
    p.standardAnswers.forEach(a => this.standardAnswers.push(this.nuovaRisposta(a)));
  }

  nuovaEsperienza(e?: Partial<ProfileDto['experiences'][number]>): FormGroup {
    return this.fb.nonNullable.group({
      id: [e?.id ?? null],
      company: [e?.company ?? '', Validators.required],
      role: [e?.role ?? '', Validators.required],
      periodLabel: [e?.periodLabel ?? ''],
      bullets: [e?.bullets ?? ''],
    });
  }

  nuovaFormazione(e?: Partial<ProfileDto['educations'][number]>): FormGroup {
    return this.fb.nonNullable.group({
      id: [e?.id ?? null],
      institution: [e?.institution ?? '', Validators.required],
      title: [e?.title ?? '', Validators.required],
      periodLabel: [e?.periodLabel ?? ''],
      details: [e?.details ?? ''],
    });
  }

  nuovaCompetenza(s?: Partial<ProfileDto['skills'][number]>): FormGroup {
    return this.fb.nonNullable.group({
      id: [s?.id ?? null],
      category: [s?.category ?? '', Validators.required],
      name: [s?.name ?? '', Validators.required],
    });
  }

  nuovaRisposta(a?: Partial<ProfileDto['standardAnswers'][number]>): FormGroup {
    return this.fb.nonNullable.group({
      id: [a?.id ?? null],
      question: [a?.question ?? '', Validators.required],
      answer: [a?.answer ?? '', Validators.required],
    });
  }

  aggiungi(lista: FormArray<FormGroup>, gruppo: FormGroup): void {
    lista.push(gruppo);
  }

  rimuovi(lista: FormArray<FormGroup>, indice: number): void {
    lista.removeAt(indice);
  }

  sposta(lista: FormArray<FormGroup>, indice: number, delta: number): void {
    const nuovo = indice + delta;
    if (nuovo < 0 || nuovo >= lista.length) return;
    const gruppo = lista.at(indice);
    lista.removeAt(indice);
    lista.insert(nuovo, gruppo);
  }

  salva(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.notifiche.errore(new Error('Controlla i campi evidenziati'));
      return;
    }
    this.salvataggio.set(true);
    const valore = this.form.getRawValue() as unknown as ProfileDto;
    this.api.updateProfile(valore).subscribe({
      next: p => { this.popola(p); this.salvataggio.set(false); this.notifiche.ok('Profilo salvato'); },
      error: err => { this.salvataggio.set(false); this.notifiche.errore(err, 'Salvataggio non riuscito'); },
    });
  }
}
