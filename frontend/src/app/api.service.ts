import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApplicationDto, ApplicationStatus, ApplyStatusDto, BrowserSite, BrowserStatusDto, ChatMessageDto, ChatResponseDto, JobImportRequest, ProfileDto, SearchSettingsDto, SearchStatusDto, SettingsDto, StatsDto } from './models';

/** Tutte le chiamate al backend Spring Boot (/api, inoltrate dal proxy in sviluppo). */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api';

  // Profilo
  getProfile(): Observable<ProfileDto> {
    return this.http.get<ProfileDto>(`${this.base}/profile`);
  }

  updateProfile(profile: ProfileDto): Observable<ProfileDto> {
    return this.http.put<ProfileDto>(`${this.base}/profile`, profile);
  }

  // Candidature
  listApplications(): Observable<ApplicationDto[]> {
    return this.http.get<ApplicationDto[]>(`${this.base}/applications`);
  }

  getApplication(id: number): Observable<ApplicationDto> {
    return this.http.get<ApplicationDto>(`${this.base}/applications/${id}`);
  }

  importJob(request: JobImportRequest): Observable<ApplicationDto> {
    return this.http.post<ApplicationDto>(`${this.base}/applications/import`, request);
  }

  analyze(id: number): Observable<ApplicationDto> {
    return this.http.post<ApplicationDto>(`${this.base}/applications/${id}/analyze`, {});
  }

  generate(id: number, cv = true, letter = true): Observable<ApplicationDto> {
    return this.http.post<ApplicationDto>(`${this.base}/applications/${id}/generate`, { cv, letter });
  }

  changeStatus(id: number, status: ApplicationStatus): Observable<ApplicationDto> {
    return this.http.patch<ApplicationDto>(`${this.base}/applications/${id}/status`, { status });
  }

  updateNotes(id: number, notes: string | null, followUpAt: string | null): Observable<ApplicationDto> {
    return this.http.patch<ApplicationDto>(`${this.base}/applications/${id}/notes`, { notes, followUpAt });
  }

  deleteApplication(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/applications/${id}`);
  }

  openFolder(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/applications/${id}/open-folder`, {});
  }

  // Impostazioni
  getSettings(): Observable<SettingsDto> {
    return this.http.get<SettingsDto>(`${this.base}/settings`);
  }

  updateSettings(settings: SettingsDto): Observable<SettingsDto> {
    return this.http.put<SettingsDto>(`${this.base}/settings`, settings);
  }

  testProvider(id: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.base}/settings/providers/${id}/test`, {});
  }

  providerModels(id: string): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/settings/providers/${id}/models`);
  }

  // Statistiche
  getStats(): Observable<StatsDto> {
    return this.http.get<StatsDto>(`${this.base}/stats`);
  }

  // Ricerca automatica
  getSearchSettings(): Observable<SearchSettingsDto> {
    return this.http.get<SearchSettingsDto>(`${this.base}/search/settings`);
  }

  updateSearchSettings(settings: SearchSettingsDto): Observable<SearchSettingsDto> {
    return this.http.put<SearchSettingsDto>(`${this.base}/search/settings`, settings);
  }

  getSearchStatus(): Observable<SearchStatusDto> {
    return this.http.get<SearchStatusDto>(`${this.base}/search/status`);
  }

  runSearch(): Observable<SearchStatusDto> {
    return this.http.post<SearchStatusDto>(`${this.base}/search/run`, {});
  }

  stopSearch(): Observable<SearchStatusDto> {
    return this.http.post<SearchStatusDto>(`${this.base}/search/stop`, {});
  }

  // Account collegati nel browser dell'app
  browserStatus(): Observable<BrowserStatusDto> {
    return this.http.get<BrowserStatusDto>(`${this.base}/browser/status`);
  }

  browserLogin(site: BrowserSite): Observable<BrowserStatusDto> {
    return this.http.post<BrowserStatusDto>(`${this.base}/browser/login/${site}`, {});
  }

  browserLogout(site: BrowserSite): Observable<BrowserStatusDto> {
    return this.http.post<BrowserStatusDto>(`${this.base}/browser/logout/${site}`, {});
  }

  browserCheck(): Observable<BrowserStatusDto> {
    return this.http.post<BrowserStatusDto>(`${this.base}/browser/check`, {});
  }

  // Assistente in chat
  chatHistory(): Observable<ChatMessageDto[]> {
    return this.http.get<ChatMessageDto[]>(`${this.base}/chat`);
  }

  chatSend(text: string): Observable<ChatResponseDto> {
    return this.http.post<ChatResponseDto>(`${this.base}/chat`, { text });
  }

  chatReset(): Observable<void> {
    return this.http.delete<void>(`${this.base}/chat`);
  }

  // Invio assistito
  applyStatus(): Observable<ApplyStatusDto> {
    return this.http.get<ApplyStatusDto>(`${this.base}/apply/status`);
  }

  applyStart(applicationId: number): Observable<ApplyStatusDto> {
    return this.http.post<ApplyStatusDto>(`${this.base}/apply/${applicationId}/start`, {});
  }

  applyConfirm(): Observable<ApplyStatusDto> {
    return this.http.post<ApplyStatusDto>(`${this.base}/apply/confirm`, {});
  }

  applyCancel(): Observable<ApplyStatusDto> {
    return this.http.post<ApplyStatusDto>(`${this.base}/apply/cancel`, {});
  }

  getApplySettings(): Observable<{ autoSubmit: boolean }> {
    return this.http.get<{ autoSubmit: boolean }>(`${this.base}/apply/settings`);
  }

  updateApplySettings(autoSubmit: boolean): Observable<{ autoSubmit: boolean }> {
    return this.http.put<{ autoSubmit: boolean }>(`${this.base}/apply/settings`, { autoSubmit });
  }
}
