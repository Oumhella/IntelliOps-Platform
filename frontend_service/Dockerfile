# Stage 1: Build Angular SPA
FROM node:20-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
# Builds plain SPA without SSR prerendering errors
RUN npm run build -- --configuration production

# Stage 2: Serve via Nginx
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

RUN rm -rf ./*

# Copy built browser files
COPY --from=build /app/dist/frontend_service/browser ./

COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
