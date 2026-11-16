import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '../core/api.service';
/** Mirror the plain-text reply and bounded navigation contract. */
export interface ChatReply {message:string; actionLabel:string|null; actionRoute:'/accounts'|'/support'|'/offers'|'close'|null;}
/** Keep prompts on the authenticated API; do not cache conversations or manufacture replies. */
@Injectable({providedIn:'root'})
export class ChatService {
 /** Bind the configured shared HTTP boundary. */
 constructor(private api:ApiService) {}
 /** Submit only a message, leaving persona selection to the verified backend identity. */
 query(message:string):Observable<ChatReply>{return this.api.post<ChatReply>('/chat/query',{message});}
}
