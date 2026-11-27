/**
 * Global test setup for the Vitest-based Angular unit-test builder.
 *
 * This application runs zoneless in production, so `zone.js` is not part of the
 * runtime bundle. Several specs still rely on Angular's `fakeAsync()` / `tick()`
 * helpers, which require a patched Zone to be present before any spec executes.
 *
 * The `@angular/build:unit-test` builder already calls
 * `TestBed.initTestEnvironment(...)`, so this file must only load Zone patches.
 */
import 'zone.js';
import 'zone.js/testing';


