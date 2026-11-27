#!/usr/bin/env python3
"""
A stand-in for GitHub's search API.

Lets the smoke test exercise the COLLECTED path without a real token or network
access, which is otherwise the one branch that never runs locally.

    usage: python3 tools/fake_github.py <port> <total_count>
"""
import sys
import json
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs

TOTAL = int(sys.argv[2]) if len(sys.argv) > 2 else 42


class Handler(BaseHTTPRequestHandler):

    def do_GET(self):
        parsed = urlparse(self.path)
        if not parsed.path.startswith("/search/issues"):
            self.send_response(404)
            self.end_headers()
            return

        # Echoed to stderr so the smoke test can show what the provider asked
        # for — the search expression is the part most likely to be wrong.
        query = parse_qs(parsed.query).get("q", [""])[0]
        auth = self.headers.get("Authorization", "<none>")
        print(f"q={query}", file=sys.stderr)
        print(f"authorization={auth}", file=sys.stderr)

        body = json.dumps({"total_count": TOTAL, "items": []}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass  # The handler already logs what matters.


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 18099
    HTTPServer(("127.0.0.1", port), Handler).serve_forever()

