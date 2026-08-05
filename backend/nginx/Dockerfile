# FROM node:22-alpine AS build
# WORKDIR /app
# COPY package*.json ./
# RUN npm ci
# COPY . .
# ARG VITE_API_URL=http://localhost:8080/api/v1
# ENV VITE_API_URL=$VITE_API_URL
# RUN npm run build
#
# FROM nginx:1.27-alpine
# COPY nginx.conf /etc/nginx/conf.d/default.conf
# COPY --from=build /app/dist /usr/share/nginx/html
# EXPOSE 80


# #-----------nginx stage----------
# FROM nginx:alpine
#
# #COPY index_active.html /usr/share/nginx/html/
# # Copy all website files
# COPY . /usr/share/nginx/html/
#
# EXPOSE 80

# ---------- Build React application ----------
FROM node:22-alpine AS build

WORKDIR /app

# Install dependencies
COPY package*.json ./
RUN npm ci

# Copy source code
COPY . .

# API URL passed during build
ARG VITE_API_URL=http://localhost:8080/api/v1
ENV VITE_API_URL=$VITE_API_URL

# Create production build
RUN npm run build


# ---------- Serve using nginx ----------
FROM nginx:1.27-alpine

# Remove default nginx content
RUN rm -rf /usr/share/nginx/html/*

# Copy React production build
COPY --from=build /app/dist /usr/share/nginx/html

# Copy nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]