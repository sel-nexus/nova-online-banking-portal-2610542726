import { Component, DestroyRef, computed, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { SupportService, SupportContent, InquiryReceipt } from './support.service';

/** Render protected help and a public contact entry without leaking directory data. */
@Component({selector:'nova-support',standalone:true,imports:[ReactiveFormsModule,RouterLink],templateUrl:'./support.component.html'})
export class SupportComponent {
 readonly contact:boolean;
 readonly content=signal<SupportContent|null>(null);
 readonly loading=signal(false);readonly sending=signal(false);
 readonly error=signal('');readonly receipt=signal<InquiryReceipt|null>(null);
 readonly search=signal('');readonly expanded=signal('');
 readonly faqs=computed(()=>{const q=this.search().trim().toLowerCase();return (this.content()?.faqs||[]).filter(f=>(f.question+' '+f.answer).toLowerCase().includes(q));});
 readonly form=new FormGroup({
  topic:new FormControl('',{nonNullable:true,validators:[Validators.required,Validators.maxLength(50),Validators.pattern(/^(LOGIN|ACCOUNTS|TRANSACTIONS|CARDS|LOANS|OFFERS|GENERAL)$/)]}),
  subject:new FormControl('',{nonNullable:true,validators:[Validators.required,Validators.maxLength(150),Validators.pattern(/\S/)]}),
  message:new FormControl('',{nonNullable:true,validators:[Validators.required,Validators.maxLength(5000),Validators.pattern(/\S/)]})
 });
 /** Restore shareable search/disclosure and read content only for a live session. */
 constructor(public auth:AuthService,private service:SupportService,private route:ActivatedRoute,private router:Router,private destroy:DestroyRef){
  this.contact=route.snapshot.data['contact']===true;
  route.queryParamMap.pipe(takeUntilDestroyed(destroy)).subscribe(params=>{this.search.set(params.get('search')||'');this.expanded.set(params.get('faq')||'');});
  if(auth.authenticated())this.load();
 }
 /** Retry protected content with explicit loading and failure states. */
 load(){if(!this.auth.authenticated())return;this.loading.set(true);this.error.set('');this.content.set(null);
  this.service.content().pipe(takeUntilDestroyed(this.destroy),finalize(()=>this.loading.set(false))).subscribe({next:c=>this.content.set(c),error:()=>this.error.set('We could not load support content. Please try again.')});
 }
 /** Update search in the URL so Back and refresh restore it. */
 searchFaq(value:string){void this.router.navigate([],{relativeTo:this.route,queryParams:{search:value||null,faq:null},queryParamsHandling:'merge'});}
 /** Toggle an accessible answer while preserving shareable state. */
 toggleFaq(id:string){void this.router.navigate([],{relativeTo:this.route,queryParams:{faq:this.expanded()===id?null:id},queryParamsHandling:'merge'});}
 /** Describe an associated field error without echoing rejected text. */
 fieldError(name:'topic'|'subject'|'message'){
  const field=this.form.controls[name];if(!field.touched||!field.invalid)return '';
  if(field.hasError('server'))return String(field.getError('server'));
  if(field.hasError('maxlength'))return name==='topic'?'Choose a supported topic.':name==='subject'?'Subject must be 1–150 characters.':'Message must be 1–5000 characters.';
  return name==='topic'?'Choose a supported topic.':name==='subject'?'Subject is required.':'Message is required.';
 }
 /** Block anonymous/invalid/duplicate sends and show only the actual server receipt. */
 send(){
  if(this.sending())return;
  this.receipt.set(null);this.error.set('');
  if(!this.auth.authenticated()){this.error.set('Please sign in to send a simulated message.');return;}
  this.form.markAllAsTouched();if(this.form.invalid)return;
  this.sending.set(true);
  this.service.submit(this.form.getRawValue()).pipe(takeUntilDestroyed(this.destroy),finalize(()=>this.sending.set(false))).subscribe({next:r=>{this.receipt.set(r);this.form.reset();},error:(e:HttpErrorResponse)=>{
   this.error.set(e.status===401?'Please sign in to send a simulated message.':'We could not record your message. Please try again.');
   const errors=e.error?.errors as Record<string,string>|undefined;
   if(errors)for(const name of ['topic','subject','message'] as const)if(errors[name]){this.form.controls[name].setErrors({server:errors[name]});this.form.controls[name].markAsTouched();}
  }});
 }
}
