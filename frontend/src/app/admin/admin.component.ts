import { Component, DestroyRef, ElementRef, ViewChild, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { NotificationService } from '../core/notification.service';
import { AdminService, AuditEvent } from './admin.service';
/** Provide reversible, explicitly simulated facilitator controls for current administrators only. */
@Component({selector:'app-admin',standalone:true,imports:[DatePipe,ReactiveFormsModule],templateUrl:'./admin.component.html'})
export class AdminComponent {
 readonly destroyRef=inject(DestroyRef);
 @ViewChild('confirmDialog') dialog?:ElementRef<HTMLDialogElement>;
 readonly username=new FormControl('miguel',{nonNullable:true,validators:[Validators.required,Validators.pattern(/^(miguel|rosa)$/)]});
 events:AuditEvent[]=[]; loading=false; auditError=''; error=''; pending=false;
 serviceError:boolean|null=null; confirmation:'reset'|'persona'|null=null;
 private trigger?:HTMLElement;
 /** Bind safe session installation, real controls and shared accessible feedback. */
 constructor(public auth:AuthService,private service:AdminService,private notices:NotificationService,private router:Router){
  this.destroyRef.onDestroy(()=>{this.dialog?.nativeElement.close();document.body.style.overflow='';});
  this.loadAudit();
 }
 /** Load history with explicit loading, empty and retry states, never stale data after failure. */
 loadAudit(){if(!this.auth.isAdmin()||this.loading)return;this.loading=true;this.auditError='';
  this.service.audit().pipe(takeUntilDestroyed(this.destroyRef),finalize(()=>this.loading=false)).subscribe({next:events=>this.events=events,error:()=>{this.events=[];this.auditError='We could not load audit history. Please retry.';}});
 }
 /** Open a native confirmation with keyboard focus, scroll lock and role re-check. */
 confirm(action:'reset'|'persona',trigger:HTMLElement){
  if(!this.auth.isAdmin()||this.pending)return;
  if(action==='persona'&&this.username.invalid){this.username.markAsTouched();return;}
  this.error='';this.confirmation=action;this.trigger=trigger;this.dialog?.nativeElement.showModal();document.body.style.overflow='hidden';
 }
 /** Close confirmation and restore its trigger; cancellation never sends a request. */
 cancel(){this.dialog?.nativeElement.close();document.body.style.overflow='';this.confirmation=null;this.trigger?.focus();}
 /** Execute only the confirmed action and block duplicate submissions. */
 execute(){
  if(!this.auth.isAdmin()||this.pending||!this.confirmation)return;
  const action=this.confirmation;const username=this.username.value;
  if(action==='persona'&&this.username.invalid){this.cancel();return;}
  this.cancel();this.pending=true;this.error='';
  if(action==='reset'){
   this.service.reset().pipe(takeUntilDestroyed(this.destroyRef),finalize(()=>this.pending=false)).subscribe({next:r=>{this.serviceError=false;this.notices.show(r.message);this.loadAudit();},error:e=>this.fail(e.status,'Reset could not be completed. No success was confirmed. Please retry.')});
  }else{
   this.service.persona(username).pipe(takeUntilDestroyed(this.destroyRef),finalize(()=>this.pending=false)).subscribe({next:s=>{this.auth.installSession(s);this.notices.show('Now signed in as '+s.user.fullName+'. Log out and sign in as admin to use Facilitator controls again.');void this.router.navigate(['/home']);},error:e=>this.fail(e.status,e.status===423?'This persona is locked. Use Facilitator Reset to unlock.':'Persona switch failed. Please retry.')});
  }
 }
 /** Persist and display server-returned simulation state, keeping disable available without deadlock. */
 simulate(enabled:boolean){if(!this.auth.isAdmin()||this.pending)return;this.pending=true;this.error='';
  this.service.simulation(enabled).pipe(takeUntilDestroyed(this.destroyRef),finalize(()=>this.pending=false)).subscribe({next:s=>{this.serviceError=s.serviceError;this.notices.show(s.serviceError?'Service-error simulation enabled for this administrator session.':'Service-error simulation disabled. Retry the business page.');this.loadAudit();},error:e=>this.fail(e.status,'Simulation setting could not be confirmed. Please retry.')});
 }
 /** Warn the current administrator session only, using the existing inactivity lifecycle. */
 timeout(){if(this.auth.isAdmin()&&!this.pending)this.auth.warnNow();}
 /** Give safe error feedback while revoked sessions are handled by the shared interceptor. */
 private fail(status:number,message:string){this.error=status===401?'Your session is no longer valid. Please sign in again.':status===403?'Administrator access is required.':message;}
}
