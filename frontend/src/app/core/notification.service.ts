import { Injectable, signal } from '@angular/core';
/** Share accessible status feedback across independently loaded pages. */
@Injectable({providedIn:'root'})
export class NotificationService {
 readonly message=signal('');
 /** Publish a user-visible status message without exposing technical details. */ show(message:string){this.message.set(message);}
 /** Dismiss the current status. */ clear(){this.message.set('');}
}

