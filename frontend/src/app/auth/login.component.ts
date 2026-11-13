import { Component, ElementRef, ViewChild, signal, OnDestroy } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { LucideAngularModule, ShieldCheck } from 'lucide-angular';
import { AuthService } from './auth.service';
/** Present the login-only localized credential journey and honest demonstration guidance. */
@Component({selector:'app-login',standalone:true,imports:[ReactiveFormsModule,RouterLink,LucideAngularModule],templateUrl:'./login.component.html'})
export class LoginComponent implements OnDestroy {
 readonly icons={ShieldCheck};
 readonly language=signal<'en'|'fil'>('en'); readonly shown=signal(false); readonly busy=signal(false); readonly error=signal(''); readonly guidance=signal('');
 @ViewChild('helpDialog') helpDialog?:ElementRef<HTMLDialogElement>;
 private trigger?:HTMLElement;
 readonly form;
 /** Initialize typed controls from the username-only preference. */
 constructor(builder:FormBuilder,private auth:AuthService,private router:Router){this.form=builder.nonNullable.group({username:[localStorage.getItem('nova.username')??'',[Validators.required,Validators.maxLength(50)]],password:['',[Validators.required,Validators.maxLength(72)]],remember:[!!localStorage.getItem('nova.username')]});}
 /** Translate login controls only; other portal content stays English. */
 text(en:string,fil:string){return this.language()==='en'?en:fil;}
 /** Mark invalid fields or submit credentials without storing the password. */
 submit(){this.form.markAllAsTouched();this.error.set('');if(this.form.invalid||this.busy())return;
  this.busy.set(true);const v=this.form.getRawValue();this.auth.login(v.username,v.password,v.remember).pipe(finalize(()=>this.busy.set(false))).subscribe({next:()=>{this.form.controls.password.reset('');void this.router.navigate(['/home']);},error:e=>this.error.set(e.status===401?'Invalid User ID or password. Please try again.':e.status===423?'Account locked due to multiple failed login attempts. Use the Facilitator Reset to unlock.':'Unable to sign in right now. Please try again.')});
 }
 /** Open native focus-trapped recovery/security guidance rather than inventing recovery operations. */
 openHelp(title:string,event:Event){this.guidance.set(title);this.trigger=event.currentTarget as HTMLElement;this.helpDialog?.nativeElement.showModal();document.body.style.overflow='hidden';}
 /** Restore the invoking control after close or Escape. */
 closeHelp(){this.helpDialog?.nativeElement.close();document.body.style.overflow='';this.trigger?.focus();}
 /** Release modal scroll lock when leaving the login page. */
 ngOnDestroy(){this.helpDialog?.nativeElement.close();document.body.style.overflow='';}
}
