import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NotificationService } from './core/notification.service';
/** Render lazy pages and shared accessible transient feedback. */
@Component({selector:'app-root',standalone:true,imports:[RouterOutlet],template:`<router-outlet/><div class="toast" role="status" aria-live="polite">@if(notices.message()){<span>{{notices.message()}}</span><button aria-label="Dismiss notification" (click)="notices.clear()">Close</button>}</div>`})
export class AppComponent { /** Bind the shared live region. */ constructor(public notices:NotificationService){} }
