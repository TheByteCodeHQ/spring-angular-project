# Frontend

Angular app for the task service. See the [project README](../README.md) for the full picture.

```bash
npm ci
npx ng serve                       # http://localhost:4200, proxies /api to http://localhost:8080
npx ng test --watch=false          # unit tests (Vitest)
npx ng build --configuration production
```

The dev server's proxy rules are in `proxy.conf.json`. Start the backend with the `dev` profile so the sign-in page can mint a token.
