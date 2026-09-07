"""Placeholder ImageBind service (P0-T02).

Serves only ``GET /health`` -> 200 so the compose stack and the gotham-web
header legend have a real target before the actual Meta ImageBind wrapper is
built in P6-T01. It performs NO embedding; every other path returns 404.
"""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json

PORT = 8081


class HealthHandler(BaseHTTPRequestHandler):
    def do_GET(self):  # noqa: N802 (stdlib naming)
        if self.path == "/health":
            body = json.dumps({"status": "UP", "service": "imagebind-service", "mode": "placeholder"}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_error(404, "Only GET /health is available in the P0 placeholder")

    def log_message(self, fmt, *args):  # keep container logs quiet but structured
        print(f"imagebind-placeholder: {fmt % args}")


if __name__ == "__main__":
    print(f"Placeholder imagebind-service listening on :{PORT} (GET /health)")
    ThreadingHTTPServer(("0.0.0.0", PORT), HealthHandler).serve_forever()
