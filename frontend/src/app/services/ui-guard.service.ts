import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class UiGuardService {
  private activeRequests = 0;
  private blockingRequests = 0;

  private readonly activeSubject = new BehaviorSubject<boolean>(false);
  private readonly blockingSubject = new BehaviorSubject<boolean>(false);

  readonly isActive$ = this.activeSubject.asObservable();
  readonly isBlocking$ = this.blockingSubject.asObservable();

  requestStarted(isBlocking: boolean): void {
    this.activeRequests += 1;
    if (isBlocking) {
      this.blockingRequests += 1;
    }
    this.activeSubject.next(this.activeRequests > 0);
    this.blockingSubject.next(this.blockingRequests > 0);
  }

  requestFinished(isBlocking: boolean): void {
    this.activeRequests = Math.max(0, this.activeRequests - 1);
    if (isBlocking) {
      this.blockingRequests = Math.max(0, this.blockingRequests - 1);
    }
    this.activeSubject.next(this.activeRequests > 0);
    this.blockingSubject.next(this.blockingRequests > 0);
  }
}

