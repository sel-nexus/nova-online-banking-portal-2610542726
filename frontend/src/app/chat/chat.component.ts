import { Component, ElementRef, ViewChild, effect, OnDestroy } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { ChatReply, ChatService } from './chat.service';
/** Describe transient displayed text; never write it to browser storage. */
interface ChatMessage {sender:'You'|'Assistant'; text:string; reply?:ChatReply;}
/** Keep a non-modal assistant flyout alive with the guarded shell, not its child pages. */
@Component({selector:'app-chat',standalone:true,imports:[ReactiveFormsModule,RouterLink],templateUrl:'./chat.component.html',styles:[`
 :host{position:relative}
 .chat-launcher{position:fixed;right:24px;bottom:24px;z-index:50;box-shadow:var(--overlay-shadow)}
 .chat-panel{position:fixed;right:24px;bottom:24px;width:380px;height:560px;max-width:calc(100vw - 32px);max-height:calc(100dvh - 32px);z-index:50;display:flex;flex-direction:column;background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);box-shadow:var(--overlay-shadow);overflow:hidden}
 .chat-header{display:flex;align-items:center;gap:8px;padding:16px;border-bottom:1px solid var(--border)}
 .chat-header>div{flex:1;min-width:0}.chat-header h2{font-size:1.1rem;margin:0}.chat-header p{margin:4px 0 0;color:var(--info);font-size:.8rem}.chat-header button{padding:8px;min-height:44px}
 .chat-scroll{flex:1;min-height:0;overflow-y:auto;padding:16px}.chat-welcome p{font-size:.875rem}.chat-welcome h3{margin:0}
 .chat-topics{display:flex;gap:8px;flex-wrap:wrap;margin:16px 0}.chat-topics button{font-size:.8rem;padding:8px;min-height:44px}
 .chat-stream{list-style:none;padding:0;margin:0}.chat-stream li{border-top:1px solid var(--border);padding:12px 0;overflow-wrap:anywhere}.chat-stream strong{font-size:.8rem;color:var(--foreground-muted)}.chat-stream p{margin:4px 0 8px;white-space:pre-wrap;font-size:.875rem}.chat-stream a{font-size:.875rem}
 .chat-composer{padding:12px 16px;border-top:1px solid var(--border)}.chat-composer label{font-size:.875rem;margin:0 0 8px}.chat-input{display:flex;gap:8px;align-items:end}.chat-input textarea{resize:vertical;min-width:0;min-height:44px;max-height:100px}.chat-composer .small{margin:8px 0 0}.chat-composer textarea[aria-invalid=true]{border-color:var(--error)}
 @media(max-width:639px){.chat-panel,.chat-launcher{right:16px;bottom:calc(16px + env(safe-area-inset-bottom,0px))}.chat-header{padding:12px}.chat-header button{font-size:.8rem}}
`]})
export class ChatComponent implements OnDestroy {
 @ViewChild('composer') composer?:ElementRef<HTMLTextAreaElement>;
 @ViewChild('launcher') launcher?:ElementRef<HTMLButtonElement>;
 @ViewChild('scroll') scroll?:ElementRef<HTMLDivElement>;
 readonly topics=['Account Balance','Card Help','Offers & Promos','Speak to Agent'];
 readonly message=new FormControl('',{nonNullable:true,validators:[Validators.required,Validators.maxLength(500),control=>control.value.trim()?null:{blank:true}]});
 readonly form=new FormGroup({message:this.message});
 messages:ChatMessage[]=[];
 open=false;
 pending=false;
 error='';
 private failedPrompt='';
 private request?:Subscription;
 private trigger?:HTMLElement;
 private identity:string|null=null;
 private focusTimer?:ReturnType<typeof setTimeout>;
 /** Clear transient state on logout or any session replacement; do not leak replies across personas. */
 constructor(public auth:AuthService,private service:ChatService){effect(()=>{const identity=auth.session()?.token??null;if(identity!==this.identity){this.identity=identity;this.clear();}});}
 /** Open from either entry point and focus the free-text composer without claiming modal semantics. */
 show(trigger?:HTMLElement){if(!this.auth.authenticated())return;this.trigger=trigger??this.launcher?.nativeElement;this.open=true;this.scheduleFocus();}
 /** Minimize without discarding the ongoing conversation or request. */
 minimize(){this.open=false;this.restoreFocus();}
 /** Close the conversation, cancel pending work and restore the originating trigger. */
 close(){this.clear();this.restoreFocus();}
 /** Close only when Escape originates within this non-modal flyout. */
 escape(event:Event){event.preventDefault();event.stopPropagation();this.close();}
 /** Validate typed input before sending; pending sends cannot duplicate a query. */
 send(){this.message.markAsTouched();if(this.message.invalid||this.pending)return;const prompt=this.message.value;this.message.reset();this.submit(prompt);}
 /** Use the same real API for each suggested topic. */
 topic(prompt:string){if(this.pending)return;this.submit(prompt);}
 /** Retry the failed prompt without duplicating the displayed user message. */
 retry(){if(this.pending||!this.failedPrompt)return;this.submit(this.failedPrompt,false);}
 /** Send text only, preserving retry state and suppressing stale replies after session changes. */
 private submit(prompt:string,append=true){
  if(!this.auth.authenticated())return;
  if(append)this.messages.push({sender:'You',text:prompt});
  this.pending=true;this.error='';this.failedPrompt=prompt;
  const token=this.auth.session()?.token;
  this.request=this.service.query(prompt).subscribe({
   next:reply=>{if(this.auth.session()?.token!==token)return;this.pending=false;this.failedPrompt='';this.messages.push({sender:'Assistant',text:reply.message,reply});this.scheduleFocus();},
   error:()=>{if(this.auth.session()?.token!==token)return;this.pending=false;this.error='We could not load an assistant reply. Please try again.';}
  });
 }
 /** Allow only real customer routes in the view even if the response is unexpectedly unsafe. */
 navigable(route:string|null){return route==='/accounts'||route==='/support'||route==='/offers';}
 /** Focus after conditional rendering and reveal the latest text only while open. */
 private scheduleFocus(){if(this.focusTimer)clearTimeout(this.focusTimer);this.focusTimer=setTimeout(()=>{if(this.open){this.composer?.nativeElement.focus();const scroll=this.scroll?.nativeElement;if(scroll)scroll.scrollTop=scroll.scrollHeight;}},0);}
 /** Restore focus after close/minimize, falling back to the mounted floating trigger. */
 private restoreFocus(){if(this.focusTimer)clearTimeout(this.focusTimer);this.focusTimer=setTimeout(()=>{const target=this.trigger?.isConnected?this.trigger:this.launcher?.nativeElement;target?.focus();},0);}
 /** Cancel and erase all memory-only state on session loss, close or destruction. */
 private clear(){this.request?.unsubscribe();this.pending=false;this.open=false;this.messages=[];this.error='';this.failedPrompt='';this.message.reset();if(this.focusTimer)clearTimeout(this.focusTimer);}
 /** Release pending HTTP and timers; a new shell starts with no retained history. */
 ngOnDestroy(){this.clear();}
}
