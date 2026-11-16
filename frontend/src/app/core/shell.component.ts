import { Component, ViewChild, ElementRef, effect, OnDestroy } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { AuthService } from '../auth/auth.service';
import { ChatComponent } from '../chat/chat.component';
/** Provide authenticated profile/logout and only real child routes; feature workers extend navigation. */
@Component({selector:'app-shell',standalone:true,imports:[RouterOutlet,RouterLink,RouterLinkActive,ReactiveFormsModule,ChatComponent],templateUrl:'./shell.component.html'})
export class ShellComponent implements OnDestroy {
 @ViewChild('idleDialog') idleDialog?:ElementRef<HTMLDialogElement>;
 private restoreFocus?:HTMLElement;
 readonly search = new FormControl('', {nonNullable:true});
 alertsOpen = false;
 /** Mirror session warning state into a native modal with focus restoration. */
 constructor(public auth:AuthService,private router:Router){effect(()=>{const warning=auth.warning();const dialog=this.idleDialog?.nativeElement;if(!dialog)return;if(warning&&!dialog.open){this.restoreFocus=document.activeElement as HTMLElement;dialog.showModal();document.body.style.overflow='hidden';}else if(!warning&&dialog.open){dialog.close();document.body.style.overflow='';this.restoreFocus?.focus();}});}
 /** Route global account discovery to the real overview without inventing a search API. */
 searchAccounts(){void this.router.navigate(['/accounts']);}
 /** Toggle an explicitly synthetic alert notice using an accessible button. */
 toggleAlerts(){this.alertsOpen=!this.alertsOpen;}
 /** Keep the session active only through an explicit warning response. */ keepAlive(){this.auth.keepAlive();}
 /** Release modal scroll lock when routing destroys the shell. */
 ngOnDestroy(){this.idleDialog?.nativeElement.close();document.body.style.overflow='';}
}
