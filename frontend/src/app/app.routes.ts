import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./dashboard/dashboard').then(m => m.Dashboard), title: 'JobPilot - Dashboard' },
  { path: 'ricerca', loadComponent: () => import('./ricerca/ricerca').then(m => m.Ricerca), title: 'JobPilot - Ricerca automatica' },
  { path: 'importa', loadComponent: () => import('./importa/importa').then(m => m.Importa), title: 'JobPilot - Importa offerta' },
  { path: 'candidature/:id', loadComponent: () => import('./candidatura/candidatura').then(m => m.Candidatura), title: 'JobPilot - Candidatura' },
  { path: 'statistiche', loadComponent: () => import('./statistiche/statistiche').then(m => m.Statistiche), title: 'JobPilot - Statistiche' },
  { path: 'profilo', loadComponent: () => import('./profilo/profilo').then(m => m.Profilo), title: 'JobPilot - Profilo' },
  { path: 'impostazioni', loadComponent: () => import('./impostazioni/impostazioni').then(m => m.Impostazioni), title: 'JobPilot - Impostazioni' },
  { path: '**', redirectTo: '' },
];
