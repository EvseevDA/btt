#!/usr/bin/env python3
"""Exercise the API against a disposable BTT application database and Camunda cluster."""

import json
import os
import subprocess
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

API = os.getenv("BTT_SMOKE_API", "http://127.0.0.1:8080/api/v1").rstrip("/")
PASSWORD = uuid.uuid4().hex
TIMEOUT = int(os.getenv("BTT_SMOKE_TIMEOUT", "60"))


def request(path, token=None, body=None, method=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    wire = Request(f"{API}/{path}", json.dumps(body).encode() if body is not None else None,
                   headers, method=method)
    try:
        with urlopen(wire, timeout=10) as response:
            status, data = response.status, response.read()
    except HTTPError as error:
        status, data = error.code, error.read()
    return status, json.loads(data) if data else None


def expect(path, status, token=None, body=None, method=None):
    actual, result = request(path, token, body, method)
    if actual != status:
        raise AssertionError(f"{method or ('POST' if body is not None else 'GET')} {path}: {actual}, expected {status}")
    return result


def wait_status(path, target, token):
    deadline = time.monotonic() + TIMEOUT
    while time.monotonic() < deadline:
        item = expect(path, 200, token)
        if item["status"] == target:
            return item
        time.sleep(0.25)
    raise AssertionError(f"{path}: status {item['status']}, expected {target}")


def login(username, password):
    return expect("auth/token", 200, body={"username": username, "password": password})["token"]


def account(username, roles=None):
    form = {"username": username, "password": PASSWORD, "email": f"{username}@example.test",
            "fullName": "BTT smoke test"}
    if roles:
        form["roleNames"] = roles
    return form


def sql(query):
    return subprocess.run([os.getenv("BTT_SMOKE_PSQL", "psql"), "-X", "-v", "ON_ERROR_STOP=1", "-Atc", query],
                          check=True, text=True, stdout=subprocess.PIPE).stdout.strip()


def onboarding(admin, suffix):
    rep_form = account(f"representative-{suffix}")
    expect("users", 201, body=rep_form)
    rep = login(rep_form["username"], PASSWORD)
    expect("users", 403, rep, account(f"unauthorized-{suffix}", ["PORTAL_ADMIN"]))
    staff_form = account(f"staff-{suffix}", ["REGISTRATION_EXECUTOR"])
    staff_account = expect("users", 201, admin, staff_form)
    staff = login(staff_form["username"], PASSWORD)
    digits = uuid.uuid4().int % 100_000_000
    payload = {"name": f"Smoke {suffix}", "inn": f"52{digits:08}", "ogrn": f"10252{digits:08}",
               "legalAddress": "Smoke address", "authorityEvidence": "Smoke evidence"}
    queue = expect("organization-registrations", 201, rep, payload)
    path = "organization-requests/" + queue["id"]
    expect(path + "/decision", 403, staff, {"approved": True})
    expect(path + "/decision", 202, admin, {"approved": True})
    organization = wait_status(path, 2, rep)["organizationId"]
    directory = expect("transporter-organizations?" + urlencode({"search": payload["inn"]}), 200, staff)
    assert [row["id"] for row in directory] == [organization]
    membership = expect("organization-memberships", 201, staff, {"organizationId": organization})
    path = "organization-requests/" + membership["id"]
    expect(path + "/decision", 403, admin, {"approved": True})
    expect(path + "/decision", 202, rep, {"approved": True})
    wait_status(path, 2, staff)
    return organization, rep, staff, staff_account["id"]


def registrations(admin, organization, rep, staff, executor):
    kinds = {entry["name"]: entry["id"] for entry in expect("transport-kinds?limit=100", 200, admin)}
    kind, alternate = kinds["Автобус"], kinds["Поезд"]
    license_data = {"licenseNumber": "Smoke license", "issuedAt": "2025-01-01",
                    "expiresAt": "2040-01-01", "issuingAuthority": "Smoke authority"}

    def create(kind_code, submitter, transporter=None, snapshot=None):
        payload = {"type": kind_code, "organizationId": organization}
        if transporter:
            payload["transporterId"] = transporter
        if snapshot:
            payload["transporterData"] = {"transportKindId": snapshot, "licenses": [license_data]}
        path = "registration-requests/" + expect("registration-requests", 201, submitter, payload)["id"]
        expect(path + "/submit", 200, submitter, method="POST")
        return path

    def claim(path):
        expect(path + "/claim", 202, admin, method="POST")
        wait_status(path, 2, admin)

    def decide(path, worker=admin, positive=True):
        result = expect(path + "/decision", 201, worker, {"type": 1 if positive else 2, "comment": "Smoke decision"})
        wait_status(path, 6, admin)
        return result["revision"]

    def approve(path, revision, status=3):
        expect(path + "/approve", 202, admin, {"revision": revision})
        return wait_status(path, status, admin)

    def register():
        path = create(1, rep, snapshot=kind)
        assert not expect(path, 200, rep).get("transporterId")
        claim(path)
        return approve(path, decide(path))["transporterId"]

    rejected = create(1, rep, snapshot=kind)
    claim(rejected)
    assert not approve(rejected, decide(rejected, positive=False), 4).get("transporterId")

    initial = create(1, rep, snapshot=kind)
    expect(initial + "/assign-executor", 202, admin, {"executorId": executor})
    wait_status(initial, 2, admin)
    revision = decide(initial, staff)
    expect(initial + "/withdraw", 403, staff, method="POST")
    expect(initial + "/return", 202, admin, {"revision": revision, "reason": "Smoke revision"})
    wait_status(initial, 7, admin)
    revised = decide(initial, staff)
    assert revised == revision + 1
    expect(initial + "/approve", 409, admin, {"revision": revision})
    transporter = approve(initial, revised)["transporterId"]

    amendment = create(2, rep, transporter, alternate)
    claim(amendment)
    approve(amendment, decide(amendment))
    withdrawal = create(3, rep, transporter)
    expect(withdrawal + "/withdraw", 202, rep, method="POST")
    wait_status(withdrawal, 5, rep)
    cancellation = create(4, admin, transporter)
    claim(cancellation)
    approve(cancellation, decide(cancellation))
    expect(cancellation, 404, rep)

    root = expect(f"transporters/{transporter}/registration-certificate", 200, rep)
    versions = expect(f"registration-certificates/{root['id']}/versions", 200, staff)
    assert root["status"] == 2 and root["currentVersionId"] == versions[0]["id"]
    assert [v["status"] for v in versions] == [1, 3, 2]
    snapshots = [expect(f"transporters/{transporter}/versions/{v['transporterVersionId']}", 200, rep)
                 for v in versions]
    assert len({v["licenses"][0]["id"] for v in snapshots}) == 3
    assert [v["transportKindId"] for v in snapshots] == [alternate, alternate, kind]

    second = register()
    cancellation = create(3, rep, second)
    claim(cancellation)
    approve(cancellation, decide(cancellation))
    return register(), kind


def booking(carrier, kind, admin, rep, staff):
    route = expect("routes", 201, admin, {"source": "Smoke Source", "dest": "Smoke Destination",
                                        "duration": 60, "transportKindId": kind})["id"]
    departure = datetime.now(timezone.utc) + timedelta(days=2)
    trip = expect("trips", 201, rep, {"routeId": route, "transporterId": carrier,
                                     "departureAt": departure.isoformat(),
                                     "arrivalAt": (departure + timedelta(hours=1)).isoformat(),
                                     "seatCodes": ["A1", "A2"], "basePrice": 99.90})["id"]
    assert expect(f"trips?routeId={route}&source=Smoke&maxPrice=100&bookableOnly=true", 200, rep)[0]["availableSeats"] == 2
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda actor: request("tickets", actor, {"tripId": trip, "seatCode": "A1"}),
                                (rep, staff)))
    assert sorted(code for code, _ in results) == [201, 409]
    winner, loser = (rep, staff) if results[0][0] == 201 else (staff, rep)
    ticket = next(result for code, result in results if code == 201)
    assert ticket["price"] == 99.90
    expect(f"tickets/{ticket['id']}", 404, loser)
    assert expect(f"tickets/{ticket['id']}/cancel", 200, winner, method="POST")["status"] == 2
    assert expect("tickets", 201, loser, {"tripId": trip})["seatCode"] == "A1"


def main():
    versions = sql("SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank;")
    assert versions.splitlines() == [str(version) for version in range(1, 11)], versions
    admin = login(os.environ["BTT_ADMIN_USERNAME"], os.environ["BTT_ADMIN_PASSWORD"])
    organization, rep, staff, executor = onboarding(admin, uuid.uuid4().hex[:10])
    carrier, kind = registrations(admin, organization, rep, staff, executor)
    booking(carrier, kind, admin, rep, staff)
    print("Migrations, onboarding, carrier workflows, version history and competitive booking passed.")


if __name__ == "__main__":
    main()
