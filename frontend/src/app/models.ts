// Tipi scambiati con il backend (specchio dei DTO Java)

export type JobSource = 'LINKEDIN' | 'INDEED' | 'ALTRO';

export type ApplicationStatus =
  | 'TROVATA' | 'DA_VALUTARE' | 'PRONTA' | 'INVIATA' | 'RISPOSTA'
  | 'COLLOQUIO' | 'OFFERTA' | 'RIFIUTATA' | 'SCADUTA';

export const STATI: { value: ApplicationStatus; label: string; colore: string }[] = [
  { value: 'TROVATA', label: 'Trovate', colore: '#90a4ae' },
  { value: 'DA_VALUTARE', label: 'Da valutare', colore: '#5c6bc0' },
  { value: 'PRONTA', label: 'Pronte', colore: '#26a69a' },
  { value: 'INVIATA', label: 'Inviate', colore: '#42a5f5' },
  { value: 'RISPOSTA', label: 'Risposta', colore: '#ab47bc' },
  { value: 'COLLOQUIO', label: 'Colloquio', colore: '#ffa726' },
  { value: 'OFFERTA', label: 'Offerta', colore: '#66bb6a' },
  { value: 'RIFIUTATA', label: 'Rifiutate', colore: '#ef5350' },
  { value: 'SCADUTA', label: 'Scadute', colore: '#bdbdbd' },
];

export function labelStato(stato: ApplicationStatus): string {
  return STATI.find(s => s.value === stato)?.label ?? stato;
}

export interface JobDto {
  id: number;
  source: JobSource;
  url?: string;
  title: string;
  company?: string;
  location?: string;
  remote: boolean;
  description: string;
  language?: string;
  foundAt: string;
}

export interface AnalysisDto {
  score: number;
  summary?: string;
  matchedRequirements?: string[];
  missingRequirements?: string[];
  keywords?: string[];
  redFlags?: string[];
  language?: string;
  model?: string;
  provider?: string;
  createdAt: string;
}

export interface ApplicationDto {
  id: number;
  profileId: number;
  status: ApplicationStatus;
  job: JobDto;
  analysis?: AnalysisDto;
  cvPath?: string;
  coverLetter?: string;
  letterPath?: string;
  sentAt?: string;
  followUpAt?: string;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface JobImportRequest {
  url?: string;
  text?: string;
  title?: string;
  company?: string;
  location?: string;
  remote?: boolean;
}

export interface ExperienceDto { id?: number; company: string; role: string; periodLabel?: string; bullets?: string; }
export interface EducationDto { id?: number; institution: string; title: string; periodLabel?: string; details?: string; }
export interface SkillDto { id?: number; category: string; name: string; }
export interface StandardAnswerDto { id?: number; question: string; answer: string; }

export interface ProfileDto {
  id?: number;
  fullName: string;
  headline: string;
  location?: string;
  phone?: string;
  email?: string;
  linkedinUrl?: string;
  githubUrl?: string;
  summary?: string;
  languages?: string;
  extraInfo?: string;
  experiences: ExperienceDto[];
  educations: EducationDto[];
  skills: SkillDto[];
  standardAnswers: StandardAnswerDto[];
}

export interface ProfileSummary {
  id: number;
  fullName: string;
  headline: string;
}

export type AiProviderType = 'CLAUDE' | 'OPENAI_COMPATIBLE' | 'NONE';

export interface ProviderDto {
  id?: string;
  name: string;
  type: AiProviderType;
  baseUrl?: string;
  apiKey?: string;
  apiKeyPresent: boolean;
  apiKeyHint?: string;
  model?: string;
  enabled: boolean;
  jsonMode: boolean;
  lastStatus?: 'OK' | 'ERRORE' | null;
  lastMessage?: string | null;
  lastCheckedAt?: string | null;
}

export interface ProviderPreset {
  key: string;
  name: string;
  type: AiProviderType;
  baseUrl?: string;
  model?: string;
  jsonMode: boolean;
  needsKey: boolean;
  note: string;
}

export interface SettingsDto {
  providers: ProviderDto[];
  presets: ProviderPreset[];
  settingsFile: string;
}

export interface SearchSettingsDto {
  keywords: string[];
  location: string;
  remote: boolean;
  days: number;
  linkedin: boolean;
  indeed: boolean;
  autoAnalyze: boolean;
  maxPerQuery: number;
  scheduled: boolean;
}

export interface SearchStatusDto {
  running: boolean;
  profileId?: number;
  profileName?: string;
  startedAt?: string;
  finishedAt?: string;
  found: number;
  imported: number;
  analyzed: number;
  errors: number;
  current: string;
  log: string[];
}

export interface ChatMessageDto {
  role: 'user' | 'assistant';
  content: string;
}

export interface ChatResponseDto {
  message: ChatMessageDto;
  provider: string;
  model: string;
  done: string[];
  navigateTo?: string | null;
}

export type BrowserSite = 'linkedin' | 'indeed';

export interface SiteSessionDto {
  loggedIn: boolean;
  known: boolean;
  checkedAt?: string | null;
  message?: string | null;
}

export interface BrowserStatusDto {
  linkedin: SiteSessionDto;
  indeed: SiteSessionDto;
  loginWindowOpen: boolean;
  browserBusy: boolean;
  browserOwner: string;
  log: string[];
}

export type ApplyOutcome = 'SUBMITTED' | 'READY_FOR_REVIEW' | 'NEEDS_INPUT' | 'EXTERNAL' | 'LOGIN_REQUIRED' | 'FAILED';

export interface ApplyStatusDto {
  running: boolean;
  windowOpen: boolean;
  applicationId?: number;
  jobTitle?: string;
  outcome?: ApplyOutcome | null;
  startedAt?: string;
  finishedAt?: string;
  unanswered: string[];
  answered: string[];
  log: string[];
}

export interface StatsDto {
  totali: number;
  perStato: Record<ApplicationStatus, number>;
  perFonte: { source: JobSource; totali: number; inviate: number; risposte: number; punteggioMedio?: number }[];
  inviate: number;
  conRisposta: number;
  tassoRisposta: number;
  colloqui: number;
  punteggioMedio?: number;
  nonAnalizzate: number;
  settimane: { inizio: string; inviate: number; trovate: number }[];
  promemoria: { applicationId: number; title: string; company?: string; tipo: 'DA_RICONTATTARE' | 'DA_ANALIZZARE'; data: string }[];
}

/** Classe CSS per il badge del punteggio. */
export function classePunteggio(score: number): string {
  if (score >= 85) return 'ottimo';
  if (score >= 65) return 'buono';
  if (score >= 45) return 'medio';
  return 'basso';
}
