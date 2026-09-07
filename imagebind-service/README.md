# imagebind-service

**P0 status: placeholder.** This container currently serves only `GET /health` → `200`
so the Docker Compose stack and the `gotham-web` header legend have a real target.

In **P6-T01** this is replaced by the in-repo Meta ImageBind wrapper that embeds
text / image / audio / video into `float[1024]` vectors (CPU inference, M4 lab).

- Port: `8081`
- Health: `GET /health`
- Not a Maven module (Docker-built).
