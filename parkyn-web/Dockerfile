FROM node:24-alpine

WORKDIR /app

RUN npm install -g @angular/cli

COPY package*.json ./

RUN npm install

COPY . .

EXPOSE 5000