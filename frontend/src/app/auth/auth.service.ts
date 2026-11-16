import { Injectable, inject, signal, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, finalize, tap, throwError } from 'rxjs';
import { ApiService } from '../core/api.service';
import { NotificationService } from '../core/notification.service';
/** Mirror only the approved safe profile fields. */
export interface SafeUser {id:string; username:string; fullName:string; role:string; lastLogin:string|null;}
/** Consume a server-issued signed bearer, never mint client tokens. */
export interface Session {token:string; tokenType:string; expiresIn:number; user:SafeUser; expiresAt?:number;}
/** Own tab-scoped session and inactivity revocation. Remembered storage contains only a username; sessionStorage remains JavaScript-readable and requires XSS-safe templates. */
@Injectable({providedIn:'root'})
export class AuthService implements OnDestroy {
 readonly session=signal<Session|null>(null);
 readonly warning=signal(false); readonly countdown=signal(30);
 private lastActivity=Date.now(); private timer:ReturnType<typeof setInterval>|undefined;
 private loggingOut=false;
 private activity=()=>{if(!this.warning())this.lastActivity=Date.now();};
 /** Restore only an unexpired tab session and register the specified activity events. */
 constructor(private api:ApiService,private router:Router,private notices:NotificationService){
  const stored=sessionStorage.getItem('nova.session');
  if(stored)try{const s=JSON.parse(stored) as Session;if(s.token&&s.user&&s.expiresAt&&s.expiresAt>Date.now())this.session.set(s);else sessionStorage.removeItem('nova.session');}catch{sessionStorage.removeItem('nova.session');}
  for(const event of ['click','keydown','mousemove'])document.addEventListener(event,this.activity);
  this.timer=setInterval(()=>this.checkIdle(),1000);
 }
 /** Authenticate with the real API and remember only the requested username. */
 login(username:string,password:string,remember:boolean){return this.api.post<Session>('/auth/login',{username,password}).pipe(tap(s=>{
  if(remember)localStorage.setItem('nova.username',username);else localStorage.removeItem('nova.username');
  this.installSession(s);
 }));}
 /** Install a backend-issued session, including later administrator persona responses. */
 installSession(session:Session){const s={...session,expiresAt:Date.now()+session.expiresIn*1000};this.session.set(s);sessionStorage.setItem('nova.session',JSON.stringify(s));this.lastActivity=Date.now();this.warning.set(false);this.countdown.set(30);}
 /** Expose controls only for the current live administrator, never a retained persona token. */
 isAdmin(){return this.authenticated()&&this.session()?.user.role==='ROLE_ADMIN';}
 /** Require a live session rather than trusting stored profile presence. */
 authenticated(){const s=this.session();if(!s)return false;if(!s.expiresAt||s.expiresAt<=Date.now()){this.expire();return false;}return true;}
 /** Revoke before clearing; on network failure still clear locally and explicitly report the revocation uncertainty. */
 logout(){if(this.loggingOut)return;this.loggingOut=true;
  this.api.post<{message:string}>('/auth/logout',{}).pipe(finalize(()=>{this.loggingOut=false;this.clear();})).subscribe({next:r=>this.notices.show(r.message),error:()=>this.notices.show('Signed out locally. Server revocation could not be confirmed.')});
 }
 /** Clear expired/unauthorized tab state without looping on login errors. */
 expire(){this.clear();this.notices.show('Your session has expired. Please sign in again.');}
 /** Close the warning and start a fresh inactivity window. */
 keepAlive(){this.warning.set(false);this.countdown.set(30);this.lastActivity=Date.now();}
 /** Expose deterministic warning advancement for the later facilitator timeout control. */
 warnNow(){this.lastActivity=Date.now()-120000;this.checkIdle();}
 /** Show a warning after two idle minutes and revoke after its thirty-second deadline. */
 private checkIdle(){const s=this.session();if(!s||this.loggingOut)return;
  if(!s.expiresAt||Date.now()>=s.expiresAt){this.expire();return;}
  const idle=Date.now()-this.lastActivity;
  if(idle>=120000){this.warning.set(true);this.countdown.set(Math.max(0,Math.ceil((150000-idle)/1000)));if(idle>=150000)this.logout();}
 }
 /** Remove only authentication state; preserve the username-only preference. */
 private clear(){this.session.set(null);sessionStorage.removeItem('nova.session');this.warning.set(false);void this.router.navigate(['/login']);}
 /** Release event handlers and interval when the injector is destroyed. */
 ngOnDestroy(){if(this.timer)clearInterval(this.timer);for(const e of ['click','keydown','mousemove'])document.removeEventListener(e,this.activity);}
}
/** Protect real feature routes using the tab's unexpired session. */
export const sessionGuard:CanActivateFn=()=>{const auth=inject(AuthService);return auth.authenticated()||inject(Router).createUrlTree(['/login']);};
/** Deny the facilitator route to customers and redirect anonymous sessions to login. */
export const adminGuard:CanActivateFn=()=>{const auth=inject(AuthService),router=inject(Router);return auth.isAdmin()||router.createUrlTree([auth.authenticated()?'/home':'/login']);};
/** Attach bearer only to our API; skip auth failures so login rejection stays inline. */
export const authInterceptor:HttpInterceptorFn=(request,next)=>{
 const auth=inject(AuthService), api=inject(ApiService);
 const ours=request.url.startsWith(api.base+'/'); const isAuth=ours&&request.url.startsWith(api.base+'/auth/');
 const token=auth.session()?.token;
 const outgoing=ours&&token&&!request.url.endsWith('/auth/login')?request.clone({setHeaders:{Authorization:'Bearer '+token}}):request;
 return next(outgoing).pipe(catchError((error:HttpErrorResponse)=>{if(ours&&!isAuth&&error.status===401)auth.expire();return throwError(()=>error);}));
};
