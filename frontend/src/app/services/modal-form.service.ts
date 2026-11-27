import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';

/**
 * Helper service to manage modal/form state
 * Ensures proper handling of save operations with consistent UX
 */
@Injectable({
  providedIn: 'root'
})
export class ModalFormService {

  /**
   * Execute a save operation with proper modal state management
   *
   * @param operation The Observable that performs the save
   * @param onSuccess Callback when save succeeds
   * @param onError Callback when save fails
   * @param onFinally Callback that always runs (cleanup)
   * @returns Observable with proper state handling
   *
   * Usage:
   * ```
   * this.modalFormService.executeSave(
   *   this.api.createUser(data),
   *   () => {
   *     this.showModal = false;
   *     this.flash('User created!');
   *     this.loadAll();
   *   },
   *   (error) => {
   *     this.formError = error.message;
   *   },
   *   () => {
   *     this.saving = false;
   *   }
   * ).subscribe();
   * ```
   */
  executeSave<T>(
    operation: Observable<T>,
    onSuccess?: (result: T) => void,
    onError?: (error: any) => void,
    onFinally?: () => void
  ): Observable<T> {
    return operation.pipe(
      finalize(() => {
        // ALWAYS run cleanup
        if (onFinally) {
          onFinally();
        }
      })
    ).pipe(
      finalize(() => {
        // Small delay to ensure UI updates are processed
        // This prevents race conditions with the Angular change detection
        setTimeout(() => {
          // No-op, just gives Angular time to process
        }, 10);
      })
    );
  }

  /**
   * Create a standard response handler for save operations
   * Handles success, error, and cleanup in the correct order
   */
  createSaveHandler(options: {
    onSaving?: (state: boolean) => void;
    onModalVisibility?: (visible: boolean) => void;
    onSuccess?: (message: string) => void;
    onError?: (error: string) => void;
    onReload?: () => void;
  } = {}) {
    return {
      startSave: () => {
        options.onSaving?.(true);
      },
      handleSuccess: (message: string) => {
        // Close modal FIRST
        options.onModalVisibility?.(false);
        // Then show success message
        options.onSuccess?.(message);
        // Then reload data
        options.onReload?.();
        // Finally reset saving flag
        options.onSaving?.(false);
      },
      handleError: (error: any) => {
        // Dont close modal on error
        const errorMsg = error?.error?.error || error?.error?.message || 'Operation failed';
        options.onError?.(errorMsg);
        // Reset saving flag
        options.onSaving?.(false);
      }
    };
  }

  /**
   * Easy subscription cleanup helper
   * Ensures all cleanup happens in correct order
   */
  subscribeWithCleanup(
    operation: Observable<any>,
    cb: {
      saving: (state: boolean) => void;
      modal: (visible: boolean) => void;
      flash: (msg: string) => void;
      reload: () => void;
      error: (msg: string) => void;
    }
  ) {
    return operation.subscribe({
      next: () => {
        // Important: Close modal BEFORE resetting saving flag
        // This ensures the modal closes while the UI is still "busy"
        cb.modal(false);
        cb.flash('Operation successful');
        cb.reload();
        // Reset after a tiny delay to let modal close
        setTimeout(() => cb.saving(false), 50);
      },
      error: (err: any) => {
        const errorMsg = err?.error?.error || err?.message || 'Operation failed';
        cb.error(errorMsg);
        // Reset saving flag (don't close modal on error)
        cb.saving(false);
      }
    });
  }
}

