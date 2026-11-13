import { Routes } from '@angular/router';
import { sessionGuard } from './auth/auth.service';
/** Enter through login; parent adds real feature children under the authenticated shell. */
export const routes:Routes=[
 {path:'',pathMatch:'full',redirectTo:'login'},
 {path:'login',title:'Sign in · Nova Bank',loadComponent:()=>import('./auth/login.component').then(m=>m.LoginComponent)},
 {path:'home',title:'Nova Bank · Your session',canActivate:[sessionGuard],loadComponent:()=>import('./core/shell.component').then(m=>m.ShellComponent)},
 {path:'**',redirectTo:'login'}
];
