import { Injectable, OnDestroy, signal } from '@angular/core';
/** Share accessible status feedback across independently loaded pages. */
@Injectable({providedIn:'root'})
export class NotificationService implements OnDestroy {
 readonly message=signal('');
 private expiry?:ReturnType<typeof setTimeout>;
 private focused=false;
 /** Publish feedback for eight seconds, restarting expiry for each new message. */
 show(message:string){this.pauseExpiry();this.message.set(message);if(!message)this.focused=false;this.resumeExpiry();}
 /** Dismiss feedback, release removed-control focus and cancel any pending expiry. */
 clear(){this.pauseExpiry();this.focused=false;this.message.set('');}
 /** Keep dismissal available while a keyboard user is interacting with feedback. */
 focus(){this.focused=true;this.pauseExpiry();}
 /** Restart the eight-second reading window after focus leaves the notification. */
 blur(){this.focused=false;this.resumeExpiry();}
 /** Cancel the current expiry without changing the feedback. */
 private pauseExpiry(){if(this.expiry!==undefined){clearTimeout(this.expiry);this.expiry=undefined;}}
 /** Expire feedback only when its dismiss control is not focused. */
 private resumeExpiry(){if(this.message()&&!this.focused){this.expiry=setTimeout(()=>this.clear(),8000);}}
 /** Release pending feedback timers when the application is destroyed. */
 ngOnDestroy(){this.clear();}
}

