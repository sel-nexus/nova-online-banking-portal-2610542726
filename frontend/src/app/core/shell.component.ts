import { Component, ViewChild, ElementRef, effect, OnDestroy } from '@angular/core';
import { RouterOutlet, RouterLink } from '@angular/router';
import { AuthService } from '../auth/auth.service';
/** Provide authenticated profile/logout and only real child routes; feature workers extend navigation. */
@Component({selector:'app-shell',standalone:true,imports:[RouterOutlet,RouterLink],templateUrl:'./shell.component.html'})
export class ShellComponent implements OnDestroy {
 @ViewChild('idleDialog') idleDialog?:ElementRef<HTMLDialogElement>;
 private restoreFocus?:HTMLElement;
 /** Mirror session warning state into a native modal with focus restoration. */
 constructor(public auth:AuthService){effect(()=>{const warning=auth.warning();const dialog=this.idleDialog?.nativeElement;if(!dialog)return;if(warning&&!dialog.open){this.restoreFocus=document.activeElement as HTMLElement;dialog.showModal();document.body.style.overflow='hidden';}else if(!warning&&dialog.open){dialog.close();document.body.style.overflow='';this.restoreFocus?.focus();}});}
 /** Keep the session active only through an explicit warning response. */ keepAlive(){this.auth.keepAlive();}
 /** Release modal scroll lock when routing destroys the shell. */
 ngOnDestroy(){this.idleDialog?.nativeElement.close();document.body.style.overflow='';}
}
