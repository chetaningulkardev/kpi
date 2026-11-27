"""List seeded users and roles, to work out which accounts can drive a demo."""
import json
import os
import urllib.request

BASE = "http://localhost:8080"


def call(path, token=None, method="GET", body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())


def unwrap(payload):
    if isinstance(payload, list):
        return payload
    return payload.get("data") or payload.get("content") or []


token = call("/api/auth/login", method="POST",
             body={"email": os.environ["E2E_ADMIN_EMAIL"], "password": os.environ["E2E_ADMIN_PASSWORD"]})["data"]["access_token"]

print("=== roles ===")
for role in unwrap(call("/api/roles", token)):
    print("  ", role.get("role_name") or role.get("roleName"))

users = unwrap(call("/api/users", token))
print("\n=== users:", len(users), "===")
by_role = {}
for user in users:
    names = [(r.get("role_name") or r.get("roleName")) for r in (user.get("roles") or [])]
    for name in names or ["(none)"]:
        by_role.setdefault(name, []).append(user.get("email"))

for name, emails in sorted(by_role.items()):
    print(f"  {name}: {len(emails)}")
    for email in emails[:3]:
        print("     ", email)
