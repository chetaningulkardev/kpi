import { Injectable } from '@angular/core';
import {
  HttpInterceptor,
  HttpRequest,
  HttpHandler,
  HttpEvent
} from '@angular/common/http';
import { Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { UiGuardService } from './ui-guard.service';

@Injectable()
export class UiActivityInterceptor implements HttpInterceptor {
  constructor(private uiGuard: UiGuardService) {}

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const isBlocking = !['GET', 'HEAD', 'OPTIONS'].includes(request.method.toUpperCase());
    this.uiGuard.requestStarted(isBlocking);

    return next.handle(request).pipe(
      finalize(() => this.uiGuard.requestFinished(isBlocking))
    );
  }
}

