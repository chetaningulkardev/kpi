# Frontend

This Angular 21 application is the browser client for the KPI platform. The
Spring Boot backend serves its production build and provides the `/api`
endpoints. During development, the Angular dev server proxies API calls to
`http://localhost:8080` using `proxy.conf.json`.

## Requirements

- Node.js 22 and npm (the project specifies npm 11.17.0)
- A running backend for authenticated and API-backed screens

## Develop

```sh
npm ci
npm start
```

Open <http://localhost:4200>. To run the frontend by itself, start the backend
separately and make sure the database is configured. See the repository
[README](../README.md) for the full-stack setup.

## Build and test

```sh
npm run build
npm test -- --watch=false
```

The unit tests use Vitest through the Angular CLI. See the
[end-to-end guide](e2e/README.md) for browser installation, live tests, and the
fixture-backed UI audit.

## Source layout

- `src/app/components/` — standalone pages and shared components
- `src/app/services/` — API clients, authentication, permissions, and UI state
- `src/app/models/` — frontend data models
- `src/app/directives/` — reusable directives
- `src/app/testing/` — test helpers
- `e2e/` — Playwright end-to-end tests
