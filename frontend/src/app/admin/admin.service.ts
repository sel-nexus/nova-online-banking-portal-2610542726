import { Injectable } from '@angular/core';
import { ApiService } from '../core/api.service';
import { Session } from '../auth/auth.service';
/** Consume safe audit metadata only, never arbitrary audit details. */
export interface AuditEvent { id:string; userId:string|null; action:string; timestamp:string; }
/** Mirror the issuing session's persisted failure setting. */
export interface SimulationState { serviceError:boolean; }
/** Keep privileged controls on the authenticated API boundary. */
@Injectable({providedIn:'root'})
export class AdminService {
 /** Bind the shared configured transport. */
 constructor(private api:ApiService) {}
 /** Load persistent audit history. */
 audit(){return this.api.get<AuditEvent[]>('/admin/audit');}
 /** Restore fixtures and return the server's exact confirmation. */
 reset(){return this.api.post<{message:string}>('/admin/reset',{});}
 /** Request an audited customer session, replacing administrator authority. */
 persona(username:string){return this.api.post<Session>('/admin/persona',{username});}
 /** Set failure only on the current administrator session. */
 simulation(serviceError:boolean){return this.api.post<SimulationState>('/admin/simulation',{serviceError});}
}
