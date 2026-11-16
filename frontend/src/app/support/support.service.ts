import { Injectable } from '@angular/core';
import { ApiService } from '../core/api.service';
/** Mirror the protected plain-text reference document. */
export interface SupportContent {contacts:{title:string;detail:string;hours:string;description:string}[];branches:{name:string;address:string;description:string;hours:string}[];faqs:{id:string;question:string;answer:string}[];topics:string[];notice:string;}
/** Submit only bounded topic and text, never an owner identifier. */
export interface InquiryRequest {topic:string;subject:string;message:string;}
/** Confirm a local receipt, not actual delivery. */
export interface InquiryReceipt {referenceNumber:string;status:'RECEIVED';estimatedResponse:string;}
/** Keep support operations on the existing configured HTTP boundary. */
@Injectable({providedIn:'root'})
export class SupportService {
 /** Bind typed transport. */ constructor(private api:ApiService) {}
 /** Retrieve protected persisted directory and FAQs. */ content(){return this.api.get<SupportContent>('/support/content');}
 /** Record a simulated enquiry; propagate failures for inline feedback. */ submit(request:InquiryRequest){return this.api.post<InquiryReceipt>('/support/inquiries',request);}
}
