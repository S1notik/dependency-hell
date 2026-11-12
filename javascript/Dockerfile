FROM node:22-alpine
WORKDIR /service
COPY tree-service.js tree-service.test.js ./
RUN node --test tree-service.test.js
USER node
EXPOSE 8084
CMD ["node", "tree-service.js"]
