#!/usr/bin/env python3
"""Minimal stdlib mock of the §13 HaloxTraffic backend, for end-to-end sync testing.

Run on the host, then `adb reverse tcp:8000 tcp:8000` so the phone's localhost:8000
reaches it. The app's BASE_URL points at http://localhost:8000/ in debug builds.

It just accepts everything (idempotent upserts) and logs what it received, so you can
watch sessions / cases / evidence arrive as the app drains its sync queue.
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = 8000


class Handler(BaseHTTPRequestHandler):
    def _send(self, obj, code=200):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _read(self):
        n = int(self.headers.get("Content-Length", 0) or 0)
        return self.rfile.read(n) if n else b""

    def log_message(self, *args):  # quieter default logging
        pass

    def do_GET(self):
        path = self.path.split("?")[0]
        print(f"GET  {self.path}", flush=True)
        if path == "/v1/config":
            self._send({"jurisdictionId": "demo", "name": "Demo", "configJson": "{}", "junctions": []})
        elif path == "/v1/junctions":
            self._send([])
        elif path == "/":
            self._send({"ok": True, "service": "halox-mock"})
        else:
            self._send({"ok": True})

    def do_POST(self):
        path = self.path.split("?")[0]
        raw = self._read()
        size = len(raw)
        # Multipart evidence uploads are binary; everything else is JSON we can peek at.
        summary = ""
        if "evidence" not in path:
            try:
                summary = " " + json.dumps(json.loads(raw))[:160]
            except Exception:
                summary = ""
        print(f"POST {path}  ({size} bytes){summary}", flush=True)

        if path == "/v1/auth/token":
            self._send({"token": "mock-token", "expiresAtMs": 9999999999999})
        elif path == "/v1/vahan-lookup":
            plate = ""
            try:
                plate = json.loads(raw).get("plate", "")
            except Exception:
                pass
            self._send({"plate": plate, "found": False})
        elif path.startswith("/v1/sessions") or path.startswith("/v1/cases"):
            self._send({"accepted": ["ok"], "rejected": []})
        else:
            self._send({"ok": True})


if __name__ == "__main__":
    print(f"Halox mock backend on http://0.0.0.0:{PORT}  (Ctrl-C to stop)", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
