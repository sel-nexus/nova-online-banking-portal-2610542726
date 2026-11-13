import { Routes } from '@angular/router';
import { sessionGuard } from './auth/auth.service';
/** Enter through login and protect each implemented child beneath the shared shell. */
export const routes:Routes=[
 {path:'',pathMatch:'full',redirectTo:'login'},
 {path:'login',title:'Sign in · Nova Bank',loadComponent:()=>import('./auth/login.component').then(m=>m.LoginComponent)},
 {path:'',canActivate:[sessionGuard],loadComponent:()=>import('./core/shell.component').then(m=>m.ShellComponent),children:[
  {path:'home',title:'Home · Nova Bank',canActivate:[sessionGuard],data:{overview:false},loadComponent:()=>import('./portfolio/portfolio.component').then(m=>m.PortfolioComponent)},
  {path:'accounts/:id',title:'Account activity · Nova Bank',canActivate:[sessionGuard],loadComponent:()=>import('./activity/activity.component').then(m=>m.ActivityComponent)},
  {path:'accounts',title:'Accounts · Nova Bank',canActivate:[sessionGuard],data:{overview:true},loadComponent:()=>import('./portfolio/portfolio.component').then(m=>m.PortfolioComponent)}
 ]},
 {path:'**',redirectTo:'login'}
];
