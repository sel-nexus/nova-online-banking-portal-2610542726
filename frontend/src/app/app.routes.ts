import { Routes } from '@angular/router';
import { sessionGuard } from './auth/auth.service';
/** Enter through login and protect each implemented child beneath the shared shell. */
export const routes:Routes=[
 {path:'',pathMatch:'full',redirectTo:'login'},
 {path:'login',title:'Sign in · Nova Bank',loadComponent:()=>import('./auth/login.component').then(m=>m.LoginComponent)},
 {path:'contact',title:'Contact · Nova Bank',data:{contact:true},loadComponent:()=>import('./support/support.component').then(m=>m.SupportComponent)},
 {path:'',canActivate:[sessionGuard],loadComponent:()=>import('./core/shell.component').then(m=>m.ShellComponent),children:[
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
