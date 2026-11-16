import { inject } from '@angular/core';
import { CanMatchFn, Routes } from '@angular/router';
import { AuthService, sessionGuard, adminGuard } from './auth/auth.service';
/** Let anonymous Contact stay public while live sessions fall through to the shared shell. */
const publicContact:CanMatchFn=()=>!inject(AuthService).authenticated();
/** Enter through login and protect each implemented child beneath the shared shell. */
export const routes:Routes=[
 {path:'',pathMatch:'full',redirectTo:'login'},
 {path:'login',title:'Sign in · Nova Bank',loadComponent:()=>import('./auth/login.component').then(m=>m.LoginComponent)},
 {path:'contact',canMatch:[publicContact],title:'Contact · Nova Bank',data:{contact:true},loadComponent:()=>import('./support/support.component').then(m=>m.SupportComponent)},
 {path:'',canActivate:[sessionGuard],loadComponent:()=>import('./core/shell.component').then(m=>m.ShellComponent),children:[
  {path:'contact',title:'Contact · Nova Bank',canActivate:[sessionGuard],data:{contact:true},loadComponent:()=>import('./support/support.component').then(m=>m.SupportComponent)},
  {path:'admin',title:'Facilitator · Nova Bank',canActivate:[adminGuard],loadComponent:()=>import('./admin/admin.component').then(m=>m.AdminComponent)},
  {path:'support',title:'Support · Nova Bank',canActivate:[sessionGuard],loadComponent:()=>import('./support/support.component').then(m=>m.SupportComponent)},
  {path:'home',title:'Home · Nova Bank',canActivate:[sessionGuard],data:{overview:false},loadComponent:()=>import('./portfolio/portfolio.component').then(m=>m.PortfolioComponent)},
  {path:'accounts/:id',title:'Account activity · Nova Bank',canActivate:[sessionGuard],loadComponent:()=>import('./activity/activity.component').then(m=>m.ActivityComponent)},
  {path:'products',canActivate:[sessionGuard],title:'Products · Nova Bank',data:{products:true},loadComponent:()=>import('./offers/offers.component').then(m=>m.OffersComponent)},
  {path:'offers/:offerId',canActivate:[sessionGuard],title:'Offer details · Nova Bank',loadComponent:()=>import('./offers/offers.component').then(m=>m.OffersComponent)},
  {path:'offers',canActivate:[sessionGuard],title:'Offers · Nova Bank',loadComponent:()=>import('./offers/offers.component').then(m=>m.OffersComponent)},
  {path:'accounts',title:'Accounts · Nova Bank',canActivate:[sessionGuard],data:{overview:true},loadComponent:()=>import('./portfolio/portfolio.component').then(m=>m.PortfolioComponent)}
 ]},
 {path:'**',redirectTo:'login'}
];
