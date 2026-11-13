import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../environments/environment';
/** Keep feature clients on one typed, configured API boundary. Money DTOs use decimal strings, never floating point calculations. */
@Injectable({providedIn:'root'})
export class ApiService {
 readonly base = environment.apiBase.replace(/\/$/, '');
 /** Bind the Angular HTTP transport. */ constructor(private http: HttpClient) {}
 /** Retrieve a safe response, optionally applying query parameters. */ get<T>(path:string, params?:HttpParams){return this.http.get<T>(this.base+path,{params});}
 /** Submit a validated domain request. */ post<T>(path:string, body:unknown){return this.http.post<T>(this.base+path,body);}
}

