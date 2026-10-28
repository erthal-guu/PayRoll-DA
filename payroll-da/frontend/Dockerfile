FROM node:20 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Endereço público do site (a API responde no mesmo endereço, em /api).
ARG VITE_API_URL
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

FROM caddy:2
COPY --from=build /app/dist /srv
COPY Caddyfile /etc/caddy/Caddyfile
