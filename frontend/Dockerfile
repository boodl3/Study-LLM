# syntax=docker/dockerfile:1

# ---- build ----------------------------------------------------------------
# bookworm-slim, not alpine: rollup/esbuild resolve platform-specific optional binaries from a
# lockfile generated on Windows, and glibc is the better-tested target. Stage is discarded.
FROM node:22-bookworm-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
# Full install including devDependencies: `npm run build` is `tsc -b && vite build`, and
# tsconfig.app.json includes src/**, which contains ChatConversation.test.tsx — so typescript,
# vitest and @testing-library types must all be present or tsc fails.
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npm run build

# ---- runtime --------------------------------------------------------------
# No VITE_* build args: src/api/client.ts uses a relative "/api/v1" base and there is no
# import.meta.env usage anywhere in src, so one image works in any environment.
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
