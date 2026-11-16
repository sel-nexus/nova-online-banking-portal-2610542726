import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { ApiService } from '../core/api.service';

/** Retrieve statement blobs through the application's registered bearer interceptor. */
@Injectable({providedIn: 'root'})
export class StatementService {
  /** Bind authenticated Angular transport and the existing configured API base. */
  constructor(private http: HttpClient, private api: ApiService) {}
  /** Fetch all activity, never forwarding the ledger's date, search or pagination state. */
  download(accountId: string): Observable<string> {
    return this.http.get(this.api.base + '/accounts/' + encodeURIComponent(accountId) + '/statement/download',
      {observe: 'response', responseType: 'blob'}).pipe(map(response => this.save(response)));
  }
  /** Accept only the server's last-four demo contract; reject paths, controls and private identifiers. */
  private filename(header: string | null): string {
    const encoded = /(?:^|;)\s*filename\*=UTF-8''([^;]+)/i.exec(header || '');
    const plain = /(?:^|;)\s*filename=(?:"([^"]*)"|([^;]*))/i.exec(header || '');
    let name = plain ? (plain[1] ?? plain[2]).trim() : '';
    if (encoded) { try { name = decodeURIComponent(encoded[1].trim()); } catch { name = ''; } }
    return /^statement-[0-9]{4}-demo\.txt$/.test(name) ? name : 'statement-demo.txt';
  }
  /** Start a browser download only for a nonempty text response, then release DOM and URL resources. */
  private save(response: HttpResponse<Blob>): string {
    const blob = response.body;
    if (!blob || blob.size === 0 || !/^text\/plain(?:;|$)/i.test(response.headers.get('Content-Type') || blob.type)) {
      throw new Error('No statement file was received.');
    }
    const name = this.filename(response.headers.get('Content-Disposition'));
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    try {
      anchor.href = url; anchor.download = name; anchor.hidden = true;
      document.body.appendChild(anchor); anchor.click();
    } finally {
      anchor.remove(); setTimeout(() => URL.revokeObjectURL(url), 0);
    }
    return name;
  }
}
