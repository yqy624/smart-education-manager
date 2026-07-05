# Deployment Notes

## Docker
Build with the bundled Dockerfile; it uses an official Maven builder image and a Java 17 runtime image.

## Nginx
Proxy `/api`, `/admin`, `/student`, `/teacher`, `/css`, `/js`, `/swagger-ui`, `/v3/api-docs` to the backend service.

## Environment
- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
