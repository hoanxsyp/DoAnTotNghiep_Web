# =====================================================================
#  Frontend - ReactJS 18 + Vite  ->  phuc vu tinh bang Nginx
# =====================================================================

# ------------------------------ Stage 1: build -----------------------
FROM node:20-alpine AS builder

WORKDIR /app

# Cache lop node_modules: chi copy manifest truoc.
COPY package.json package-lock.json* ./

# `npm ci` nhanh va tai lap duoc, nhung doi hoi package-lock.json. Neu chua
# co lock file thi lui ve `npm install` de build van chay duoc.
RUN if [ -f package-lock.json ]; then npm ci --no-audit --no-fund; \
    else npm install --no-audit --no-fund; fi

COPY . .

# Vite nhung bien VITE_* vao bundle luc BUILD (khong phai luc chay), nen
# phai truyen qua build-arg. Mac dinh "/api" -> di qua reverse proxy cua
# chinh Nginx nay, nho vay khong hardcode host backend vao bundle.
ARG VITE_API_BASE_URL=/api
ARG VITE_APP_NAME=Webtro
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_APP_NAME=$VITE_APP_NAME

RUN npm run build

# ----------------------------- Stage 2: runtime ----------------------
FROM nginx:1.27-alpine AS runtime

RUN rm -rf /usr/share/nginx/html/* \
    && rm /etc/nginx/conf.d/default.conf

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/webtro.conf

EXPOSE 80

# Dùng 127.0.0.1 (KHÔNG "localhost"): trong container, "localhost" có thể phân giải ra IPv6 ::1
# trong khi nginx chỉ lắng nghe IPv4 (listen 80) -> healthcheck bị "connection refused" dù SPA vẫn
# phục vụ bình thường qua port mapping của Docker.
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
    CMD wget -q --spider http://127.0.0.1/healthz || exit 1

CMD ["nginx", "-g", "daemon off;"]
