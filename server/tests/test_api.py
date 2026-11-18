import importlib
import io
import json
import os
import uuid
import zipfile

import pytest
from fastapi.testclient import TestClient


@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("BABYLOG_DATA_DIR", str(tmp_path))
    monkeypatch.setenv("BABYLOG_JWT_SECRET", "test-only-secret-that-is-at-least-32-bytes-long")
    monkeypatch.setenv("BABYLOG_UPDATE_SYNC_ENABLED", "0")
    import app.main as main
    importlib.reload(main)
    with TestClient(main.app) as test_client:
        yield test_client


def register(client, email):
    response = client.post("/api/auth/register", json={"email": email, "password": "correct-horse-battery"})
    assert response.status_code == 200, response.text
    return response.json()["access_token"]


def auth(token):
    return {"Authorization": f"Bearer {token}"}



def test_password_change_revokes_existing_access_tokens(client):
    token = register(client, "token-revoke@example.test")
    assert client.get("/api/me", headers=auth(token)).status_code == 200
    changed = client.post("/api/account/password", headers=auth(token), json={
        "current_password": "correct-horse-battery",
        "new_password": "a-new-secure-password"
    })
    assert changed.status_code == 200
    assert client.get("/api/me", headers=auth(token)).status_code == 401
    login = client.post("/api/auth/login", json={
        "email": "token-revoke@example.test",
        "password": "a-new-secure-password"
    })
    assert login.status_code == 200
    assert client.get("/api/me", headers=auth(login.json()["access_token"])).status_code == 200


def test_login_attempts_are_rate_limited(client):
    register(client, "login-limit@example.test")
    for _ in range(8):
        response = client.post("/api/auth/login", json={
            "email": "login-limit@example.test", "password": "wrong-password"
        })
        assert response.status_code == 401
    blocked = client.post("/api/auth/login", json={
        "email": "login-limit@example.test", "password": "correct-horse-battery"
    })
    assert blocked.status_code == 429


def test_photo_upload_rejects_mismatched_file_content(client):
    token = register(client, "photo-mime@example.test")
    family_id = client.post("/api/families", headers=auth(token), json={"name": "photos"}).json()["family_id"]
    response = client.post("/api/photos/upload", headers=auth(token), params={
        "family_id": family_id, "photo_id": str(uuid.uuid4())
    }, files={"file": ("not-a-photo.jpg", b"this is not jpeg", "image/jpeg")})
    assert response.status_code == 415


def test_health_and_auth(client):
    assert client.get("/health").json()["status"] == "ok"
    token = register(client, "parent1@example.test")
    assert client.get("/api/me", headers=auth(token)).status_code == 200
    assert client.post("/api/auth/login", json={
        "email": "parent1@example.test", "password": "correct-horse-battery"
    }).status_code == 200


def test_family_invite_and_membership_sync(client):
    owner = register(client, "parent1@example.test")
    guest = register(client, "parent2@example.test")
    family = client.post("/api/families", headers=auth(owner), json={"name": "家庭"}).json()
    family_id = family["family_id"]
    invite = client.post(f"/api/families/{family_id}/invites", headers=auth(owner))
    assert invite.status_code == 200
    joined = client.post("/api/families/join", headers=auth(guest), json={"code": invite.json()["invite_code"]})
    assert joined.status_code == 200
    assert client.post("/api/families/join", headers=auth(guest), json={"code": invite.json()["invite_code"]}).status_code == 400

    change_id = str(uuid.uuid4())
    operation_id = str(uuid.uuid4())
    body = {"family_id": family_id, "changes": [{
        "operation_id": operation_id, "entity_type": "event", "entity_id": change_id,
        "operation": "upsert", "payload": {"eventType": "FEEDING", "amountMl": 120},
        "base_version": 0
    }]}
    pushed = client.post("/api/sync/push", headers=auth(owner), json=body)
    assert pushed.status_code == 200
    assert len(pushed.json()["accepted"]) == 1
    assert pushed.json()["accepted"][0]["version"] == 1

    duplicate = client.post("/api/sync/push", headers=auth(owner), json=body)
    assert duplicate.status_code == 200
    assert duplicate.json()["accepted"][0]["duplicate"] is True
    assert duplicate.json()["accepted"][0]["version"] == 1

    pulled = client.get("/api/sync/pull", headers=auth(guest), params={"family_id": family_id, "cursor": 0})
    assert pulled.status_code == 200
    assert pulled.json()["items"][0]["payload"]["amountMl"] == 120

    stale = {**body, "changes": [{**body["changes"][0], "operation_id": str(uuid.uuid4()), "payload": {"amountMl": 150}}]}
    conflict = client.post("/api/sync/push", headers=auth(guest), json=stale)
    assert conflict.status_code == 200
    assert conflict.json()["conflicts"][0]["reason"] == "version_conflict"
    assert conflict.json()["conflicts"][0]["server_version"] == 1

    # Retrying the unchanged local payload with the returned version is safe.
    rebased = {**stale, "changes": [{
        **stale["changes"][0], "base_version": conflict.json()["conflicts"][0]["server_version"]
    }]}
    retried = client.post("/api/sync/push", headers=auth(guest), json=rebased)
    assert retried.status_code == 200
    assert len(retried.json()["accepted"]) == 1
    assert retried.json()["accepted"][0]["version"] == 2


def test_family_data_isolation(client):
    first = register(client, "first@example.test")
    second = register(client, "second@example.test")
    family_id = client.post("/api/families", headers=auth(first), json={"name": "private"}).json()["family_id"]
    response = client.get("/api/sync/pull", headers=auth(second), params={"family_id": family_id})
    assert response.status_code == 403


def test_sync_status_fingerprint_tracks_active_versions_and_family_access(client):
    owner = register(client, "sync-status@example.test")
    outsider = register(client, "sync-outsider@example.test")
    family_id = client.post("/api/families", headers=auth(owner), json={"name": "status"}).json()["family_id"]

    empty = client.get("/api/sync/status", headers=auth(owner), params={"family_id": family_id})
    assert empty.status_code == 200
    assert empty.json()["active_count"] == 0
    empty_digest = empty.json()["digest"]
    assert len(empty_digest) == 64

    entity_id = str(uuid.uuid4())
    pushed = client.post("/api/sync/push", headers=auth(owner), json={"family_id": family_id, "changes": [{
        "operation_id": str(uuid.uuid4()), "entity_type": "event", "entity_id": entity_id,
        "operation": "upsert", "payload": {"eventType": "FEEDING"}, "base_version": 0
    }]})
    assert pushed.status_code == 200
    status = client.get("/api/sync/status", headers=auth(owner), params={"family_id": family_id})
    assert status.status_code == 200
    assert status.json()["active_count"] == 1
    assert status.json()["active_counts"] == {"event": 1}
    assert status.json()["digest"] != empty_digest

    denied = client.get("/api/sync/status", headers=auth(outsider), params={"family_id": family_id})
    assert denied.status_code == 403

    deleted = client.post("/api/sync/push", headers=auth(owner), json={"family_id": family_id, "changes": [{
        "operation_id": str(uuid.uuid4()), "entity_type": "event", "entity_id": entity_id,
        "operation": "delete", "payload": {"id": entity_id}, "base_version": 1
    }]})
    assert deleted.status_code == 200
    after_delete = client.get("/api/sync/status", headers=auth(owner), params={"family_id": family_id})
    assert after_delete.json()["active_count"] == 0
    assert after_delete.json()["digest"] == empty_digest


def test_baby_avatar_upload_download_and_delete(client):
    token = register(client, "baby-avatar@example.test")
    family_id = client.post("/api/families", headers=auth(token), json={"name": "avatar"}).json()["family_id"]
    baby_id = str(uuid.uuid4())
    uploaded = client.post(
        f"/api/babies/{baby_id}/avatar", headers=auth(token), params={"family_id": family_id},
        files={"file": ("avatar.jpg", b"\xff\xd8\xffavatar", "image/jpeg")}
    )
    assert uploaded.status_code == 200, uploaded.text
    downloaded = client.get(f"/api/babies/{baby_id}/avatar", headers=auth(token), params={"family_id": family_id})
    assert downloaded.status_code == 200
    assert downloaded.content == b"\xff\xd8\xffavatar"
    deleted = client.delete(f"/api/babies/{baby_id}/avatar", headers=auth(token), params={"family_id": family_id})
    assert deleted.status_code == 200
    assert client.get(f"/api/babies/{baby_id}/avatar", headers=auth(token), params={"family_id": family_id}).status_code == 404


def test_photo_upload_and_download(client):
    token = register(client, "photos@example.test")
    family_id = client.post("/api/families", headers=auth(token), json={"name": "photos"}).json()["family_id"]
    photo_id = str(uuid.uuid4())
    uploaded = client.post(
        "/api/photos/upload", headers=auth(token),
        params={"family_id": family_id, "photo_id": photo_id},
        files={"file": ("test.jpg", b"\xff\xd8\xfftest-image", "image/jpeg")}
    )
    assert uploaded.status_code == 200, uploaded.text
    downloaded = client.get(f"/api/photos/{photo_id}", headers=auth(token), params={"family_id": family_id})
    assert downloaded.status_code == 200
    assert downloaded.content == b"\xff\xd8\xfftest-image"

def test_photo_metadata_sync_and_delete(client):
    token = register(client, "photo-sync@example.test")
    family_id = client.post("/api/families", headers=auth(token), json={"name": "photos"}).json()["family_id"]
    photo_id = str(uuid.uuid4())
    uploaded = client.post(
        "/api/photos/upload", headers=auth(token),
        params={"family_id": family_id, "photo_id": photo_id},
        files={"file": ("test.jpg", b"\xff\xd8\xffphoto-bytes", "image/jpeg")}
    )
    assert uploaded.status_code == 200

    metadata = {"id": photo_id, "babyId": str(uuid.uuid4()), "caption": "满月", "takenAt": 123456789,
                "createdAt": 123456789, "updatedAt": 123456789, "mimeType": "image/jpeg"}
    pushed = client.post("/api/sync/push", headers=auth(token), json={"family_id": family_id, "changes": [{
        "operation_id": str(uuid.uuid4()), "entity_type": "photo", "entity_id": photo_id,
        "operation": "upsert", "payload": metadata, "base_version": 0
    }]})
    assert pushed.status_code == 200
    assert pushed.json()["accepted"][0]["version"] == 1

    pulled = client.get("/api/sync/pull", headers=auth(token), params={"family_id": family_id, "cursor": 0})
    assert pulled.json()["items"][0]["entity_type"] == "photo"
    assert pulled.json()["items"][0]["payload"]["caption"] == "满月"

    deleted = client.post("/api/sync/push", headers=auth(token), json={"family_id": family_id, "changes": [{
        "operation_id": str(uuid.uuid4()), "entity_type": "photo", "entity_id": photo_id,
        "operation": "delete", "payload": {"id": photo_id}, "base_version": 1
    }]})
    assert deleted.status_code == 200
    assert deleted.json()["accepted"][0]["version"] == 2
    assert client.get(f"/api/photos/{photo_id}", headers=auth(token), params={"family_id": family_id}).status_code == 404
    pulled = client.get("/api/sync/pull", headers=auth(token), params={"family_id": family_id, "cursor": 0})
    assert pulled.json()["items"][-1]["operation"] == "delete"


def test_pull_skips_photo_upsert_after_photo_was_deleted(client):
    token = register(client, "deleted-photo@example.test")
    family_id = client.post("/api/families", headers=auth(token), json={"name": "photos"}).json()["family_id"]
    photo_id = str(uuid.uuid4())
    uploaded = client.post(
        "/api/photos/upload", headers=auth(token),
        params={"family_id": family_id, "photo_id": photo_id},
        files={"file": ("old.jpg", b"\xff\xd8\xffold-photo-bytes", "image/jpeg")}
    )
    assert uploaded.status_code == 200, uploaded.text

    metadata = {"id": photo_id, "babyId": str(uuid.uuid4()), "caption": "已删除照片",
                "takenAt": 123456789, "createdAt": 123456789, "updatedAt": 123456789,
                "mimeType": "image/jpeg"}
    pushed = client.post("/api/sync/push", headers=auth(token), json={"family_id": family_id, "changes": [{
        "operation_id": str(uuid.uuid4()), "entity_type": "photo", "entity_id": photo_id,
        "operation": "upsert", "payload": metadata, "base_version": 0
    }]})
    assert pushed.status_code == 200

    deleted = client.post("/api/sync/push", headers=auth(token), json={"family_id": family_id, "changes": [{
        "operation_id": str(uuid.uuid4()), "entity_type": "photo", "entity_id": photo_id,
        "operation": "delete", "payload": {"id": photo_id}, "base_version": 1
    }]})
    assert deleted.status_code == 200

    pulled = client.get("/api/sync/pull", headers=auth(token), params={"family_id": family_id, "cursor": 0})
    assert pulled.status_code == 200
    assert [item["operation"] for item in pulled.json()["items"]] == ["delete"]
    assert pulled.json()["items"][0]["entity_id"] == photo_id
    assert pulled.json()["scanned_count"] == 2
    assert pulled.json()["next_cursor"] > 0


def test_owner_authoritative_snapshot_replaces_family_records(client):
    owner = register(client, "snapshot-owner@example.test")
    member = register(client, "snapshot-member@example.test")
    family_id = client.post("/api/families", headers=auth(owner), json={"name": "snapshot"}).json()["family_id"]
    invite = client.post(f"/api/families/{family_id}/invites", headers=auth(owner)).json()["invite_code"]
    assert client.post("/api/families/join", headers=auth(member), json={"code": invite}).status_code == 200

    baby_id = str(uuid.uuid4())
    old_event_id = str(uuid.uuid4())
    for entity_type, entity_id, payload in [
        ("baby", baby_id, {"id": baby_id, "displayName": "旧名字", "hasAvatar": False}),
        ("event", old_event_id, {"id": old_event_id, "babyId": baby_id, "eventType": "FEEDING", "amountMl": 90}),
    ]:
        pushed = client.post("/api/sync/push", headers=auth(owner), json={"family_id": family_id, "changes": [{
            "operation_id": str(uuid.uuid4()), "entity_type": entity_type, "entity_id": entity_id,
            "operation": "upsert", "payload": payload, "base_version": 0
        }]})
        assert pushed.status_code == 200

    denied = client.post("/api/sync/authoritative-snapshot", headers=auth(member), json={
        "family_id": family_id, "items": []
    })
    assert denied.status_code == 403

    new_event_id = str(uuid.uuid4())
    snapshot = client.post("/api/sync/authoritative-snapshot", headers=auth(owner), json={
        "family_id": family_id,
        "items": [
            {"operation_id": str(uuid.uuid4()), "entity_type": "baby", "entity_id": baby_id,
             "operation": "upsert", "payload": {"id": baby_id, "displayName": "第一台手机", "hasAvatar": False}},
            {"operation_id": str(uuid.uuid4()), "entity_type": "event", "entity_id": new_event_id,
             "operation": "upsert", "payload": {"id": new_event_id, "babyId": baby_id, "eventType": "FEEDING", "amountMl": 120}},
        ]
    })
    assert snapshot.status_code == 200, snapshot.text
    assert snapshot.json()["replaced"] is True
    assert snapshot.json()["changes"] == 3

    pulled = client.get("/api/sync/pull", headers=auth(member), params={"family_id": family_id, "cursor": 0})
    assert pulled.status_code == 200
    latest = {}
    for item in pulled.json()["items"]:
        latest[(item["entity_type"], item["entity_id"])] = item
    assert latest[("baby", baby_id)]["payload"]["displayName"] == "第一台手机"
    assert latest[("event", old_event_id)]["operation"] == "delete"
    assert latest[("event", new_event_id)]["payload"]["amountMl"] == 120


def test_same_account_devices_resolve_to_one_automatic_family(client):
    token = register(client, "same-account@example.test")

    first = client.get("/api/me", headers=auth(token))
    assert first.status_code == 200
    first_families = first.json()["families"]
    assert len(first_families) == 1
    family_id = first_families[0]["id"]

    login = client.post("/api/auth/login", json={
        "email": "same-account@example.test", "password": "correct-horse-battery"
    })
    assert login.status_code == 200
    second = client.get("/api/me", headers=auth(login.json()["access_token"]))
    assert second.status_code == 200
    assert len(second.json()["families"]) == 1
    assert second.json()["families"][0]["id"] == family_id


def test_username_account_login_and_security_changes(client):
    registered = client.post("/api/auth/register", json={
        "username": "family_parent", "password": "correct-horse-battery"
    })
    assert registered.status_code == 200, registered.text
    token = registered.json()["access_token"]
    assert registered.json()["username"] == "family_parent"

    profile = client.get("/api/me", headers=auth(token))
    assert profile.status_code == 200
    assert profile.json()["username"] == "family_parent"

    login = client.post("/api/auth/login", json={
        "username": "FAMILY_PARENT", "password": "correct-horse-battery"
    })
    assert login.status_code == 200

    renamed = client.post("/api/account/username", headers=auth(token), json={
        "username": "baby_family", "current_password": "correct-horse-battery"
    })
    assert renamed.status_code == 200
    assert renamed.json()["username"] == "baby_family"
    assert client.post("/api/auth/login", json={
        "username": "family_parent", "password": "correct-horse-battery"
    }).status_code == 401

    changed = client.post("/api/account/password", headers=auth(token), json={
        "current_password": "correct-horse-battery", "new_password": "new-secure-password"
    })
    assert changed.status_code == 200
    assert client.post("/api/auth/login", json={
        "username": "baby_family", "password": "correct-horse-battery"
    }).status_code == 401
    assert client.post("/api/auth/login", json={
        "username": "baby_family", "password": "new-secure-password"
    }).status_code == 200


def test_legacy_email_account_can_login_and_change_username(client):
    token = register(client, "legacy-parent@example.test")
    login = client.post("/api/auth/login", json={
        "email": "legacy-parent@example.test", "password": "correct-horse-battery"
    })
    assert login.status_code == 200
    changed = client.post("/api/account/username", headers=auth(token), json={
        "username": "legacy_parent", "current_password": "correct-horse-battery"
    })
    assert changed.status_code == 200
    # Existing email-based login remains valid for older app versions.
    assert client.post("/api/auth/login", json={
        "email": "legacy-parent@example.test", "password": "correct-horse-battery"
    }).status_code == 200


def test_nas_app_update_metadata_and_download(client, tmp_path):
    from app import main

    update_dir = tmp_path / "updates"
    update_dir.mkdir()
    apk_bytes = b"fake-signed-apk-content"
    apk_name = "BabyLog-v1.2.3.apk"
    (update_dir / apk_name).write_bytes(apk_bytes)
    digest = __import__("hashlib").sha256(apk_bytes).hexdigest()
    (update_dir / "latest.json").write_text(__import__("json").dumps({
        "available": True, "tag": "v1.2.3", "version": "1.2.3",
        "name": "BabyLog v1.2.3", "notes": "更新说明", "size": len(apk_bytes),
        "sha256": digest, "file_name": apk_name,
        "download_path": "/api/app-update/download"
    }), "utf-8")
    (update_dir / "status.json").write_text(__import__("json").dumps({
        "last_check": 100, "last_success": 100, "last_error": None,
        "current_tag": "v1.2.3"
    }), "utf-8")

    latest = client.get("/api/app-update/latest")
    assert latest.status_code == 200
    assert latest.json()["available"] is True
    assert latest.json()["version"] == "1.2.3"
    assert latest.json()["sha256"] == digest
    assert latest.json()["sync_status"]["last_error"] is None

    status = client.get("/api/app-update/status")
    assert status.status_code == 200
    assert status.json()["current_tag"] == "v1.2.3"

    download = client.get("/api/app-update/download")
    assert download.status_code == 200
    assert download.content == apk_bytes
    assert download.headers["x-checksum-sha256"] == digest


def test_nas_app_update_download_rejects_missing_or_unsafe_file(client, tmp_path):
    from app import main

    missing = client.get("/api/app-update/download")
    assert missing.status_code == 404

    update_dir = tmp_path / "updates"
    update_dir.mkdir(exist_ok=True)
    (update_dir / "latest.json").write_text(__import__("json").dumps({
        "available": True, "file_name": "../outside.apk"
    }), "utf-8")
    assert client.get("/api/app-update/download").status_code == 404


def test_nas_release_mirror_downloads_official_release_and_hashes_apk(client, tmp_path, monkeypatch):
    import hashlib
    import json
    from app import main

    apk_bytes = b"release-apk-payload"
    release = {
        "draft": False,
        "prerelease": False,
        "tag_name": "v9.8.7",
        "name": "BabyLog v9.8.7",
        "body": "更新说明",
        "published_at": "2026-11-15T00:00:00Z",
        "assets": [{
            "name": "BabyLog-v9.8.7.apk",
            "browser_download_url": "https://github.com/rinzaki007/BabyLog/releases/download/v9.8.7/BabyLog-v9.8.7.apk",
            "size": len(apk_bytes)
        }]
    }

    class FakeResponse:
        def __init__(self, payload):
            self.payload = payload
            self.read_once = False
        def __enter__(self):
            return self
        def __exit__(self, *args):
            return False
        def read(self, size=-1):
            if self.read_once:
                return b""
            self.read_once = True
            return self.payload

    responses = [FakeResponse(json.dumps(release).encode()), FakeResponse(apk_bytes)]
    def fake_urlopen(request, timeout):
        assert timeout in (25, 60)
        return responses.pop(0)

    monkeypatch.setattr("urllib.request.urlopen", fake_urlopen)
    metadata = main.refresh_app_release()

    assert metadata["tag"] == "v9.8.7"
    assert metadata["sha256"] == hashlib.sha256(apk_bytes).hexdigest()
    assert (tmp_path / "updates" / "BabyLog-v9.8.7.apk").read_bytes() == apk_bytes
    assert json.loads((tmp_path / "updates" / "latest.json").read_text("utf-8"))["version"] == "9.8.7"
    assert json.loads((tmp_path / "updates" / "status.json").read_text("utf-8"))["last_error"] is None


def test_nas_release_metadata_retries_transient_tls_disconnect(monkeypatch):
    import urllib.request
    from app import main

    attempts = {"count": 0}

    class FakeResponse:
        def __enter__(self):
            return self
        def __exit__(self, *args):
            return False
        def read(self, size=-1):
            return b'{"tag_name":"v1.0.0"}'

    def flaky_urlopen(request, timeout):
        attempts["count"] += 1
        if attempts["count"] == 1:
            raise OSError("SSL: UNEXPECTED_EOF_WHILE_READING")
        return FakeResponse()

    monkeypatch.setattr(urllib.request, "urlopen", flaky_urlopen)
    monkeypatch.setattr(main.time, "sleep", lambda _seconds: None)
    request = urllib.request.Request("https://api.github.com/test")
    assert main._read_update_url_retry(request, timeout=5, max_bytes=1024) == b'{"tag_name":"v1.0.0"}'
    assert attempts["count"] == 2


def test_nas_release_asset_retries_transient_tls_disconnect(tmp_path, monkeypatch):
    import hashlib
    import urllib.request
    from app import main

    payload = b"apk-after-retry"
    attempts = {"count": 0}

    class FakeResponse:
        def __init__(self):
            self.read_once = False
        def __enter__(self):
            return self
        def __exit__(self, *args):
            return False
        def read(self, size=-1):
            if self.read_once:
                return b""
            self.read_once = True
            return payload

    def flaky_urlopen(request, timeout):
        attempts["count"] += 1
        if attempts["count"] == 1:
            raise OSError("SSL: UNEXPECTED_EOF_WHILE_READING")
        return FakeResponse()

    monkeypatch.setattr(urllib.request, "urlopen", flaky_urlopen)
    monkeypatch.setattr(main.time, "sleep", lambda _seconds: None)
    temp = tmp_path / "release.apk.part"
    size, digest = main._download_update_asset_retry(
        urllib.request.Request("https://github.com/rinzaki007/BabyLog/releases/download/test.apk"),
        temp, len(payload), hashlib.sha256(payload).hexdigest()
    )
    assert attempts["count"] == 2
    assert size == len(payload)
    assert digest == hashlib.sha256(payload).hexdigest()
    assert temp.read_bytes() == payload


def test_nas_release_rate_limit_uses_reset_header_without_repeating_api_call(monkeypatch):
    import email.message
    import urllib.error
    import urllib.request
    from app import main

    headers = email.message.Message()
    headers["X-RateLimit-Remaining"] = "0"
    headers["X-RateLimit-Reset"] = "1700000120"
    error = urllib.error.HTTPError(
        "https://api.github.com/releases/latest", 403, "rate limited", headers,
        __import__("io").BytesIO(b'{"message":"API rate limit exceeded"}')
    )
    attempts = {"count": 0}
    def limited_urlopen(request, timeout):
        attempts["count"] += 1
        raise error

    monkeypatch.setattr(urllib.request, "urlopen", limited_urlopen)
    monkeypatch.setattr(main.time, "time", lambda: 1700000000)
    monkeypatch.setattr(main.time, "sleep", lambda _seconds: pytest.fail("rate limits must not be retried immediately"))
    request = urllib.request.Request("https://api.github.com/releases/latest")
    with pytest.raises(main.GitHubRateLimitError) as caught:
        main._read_update_url_retry(request, timeout=5, max_bytes=1024)
    assert attempts["count"] == 1
    assert caught.value.retry_at == 1700000125

    secondary = urllib.error.HTTPError(
        "https://api.github.com/releases/latest", 403, "secondary rate limit",
        email.message.Message(), __import__("io").BytesIO(b'{"message":"secondary rate limit"}')
    )
    assert main._rate_limit_retry_at(secondary, 0) == 1700000060
    assert main._rate_limit_retry_at(secondary, 2) == 1700000240


def test_nas_release_rate_limit_falls_back_to_atom_and_downloads_apk(client, tmp_path, monkeypatch):
    import hashlib
    import io
    import json
    import email.message
    import urllib.error
    import urllib.request
    from app import main

    monkeypatch.setenv("BABYLOG_GITHUB_TOKEN", "test-read-only-token")
    apk_bytes = b"apk-from-atom-fallback"
    feed = """<?xml version="1.0" encoding="UTF-8"?>
    <feed xmlns="http://www.w3.org/2005/Atom">
      <entry>
        <title>BabyLog v9.8.8</title>
        <id>tag:github.com,2008:Repository/123/v9.8.8</id>
        <updated>2026-11-15T04:00:00Z</updated>
        <link rel="alternate" href="https://github.com/rinzaki007/BabyLog/releases/tag/v9.8.8"/>
        <content type="html">修复更新镜像同步</content>
      </entry>
    </feed>""".encode("utf-8")
    calls = []

    class FakeResponse:
        def __init__(self, payload):
            self.payload = payload
            self.read_once = False
        def __enter__(self):
            return self
        def __exit__(self, *args):
            return False
        def read(self, size=-1):
            if self.read_once:
                return b""
            self.read_once = True
            return self.payload

    def fake_urlopen(request, timeout):
        url = request.full_url
        calls.append((url, request.headers))
        if "api.github.com" in url:
            assert request.get_header("Authorization") == "Bearer test-read-only-token"
            headers = email.message.Message()
            headers["X-RateLimit-Remaining"] = "0"
            headers["X-RateLimit-Reset"] = str(int(main.time.time()) + 600)
            raise urllib.error.HTTPError(
                url, 403, "rate limit exceeded", headers,
                io.BytesIO(b'{"message":"API rate limit exceeded"}')
            )
        if url == main.UPDATE_RELEASE_FEED:
            return FakeResponse(feed)
        if url.endswith("/v9.8.8/app-release.apk"):
            return FakeResponse(apk_bytes)
        raise AssertionError(f"unexpected URL: {url}")

    monkeypatch.setattr(urllib.request, "urlopen", fake_urlopen)
    metadata = main.refresh_app_release()

    assert metadata["tag"] == "v9.8.8"
    assert metadata["size"] == len(apk_bytes)
    assert metadata["sha256"] == hashlib.sha256(apk_bytes).hexdigest()
    assert (tmp_path / "updates" / "BabyLog-v9.8.8.apk").read_bytes() == apk_bytes
    status = json.loads((tmp_path / "updates" / "status.json").read_text("utf-8"))
    assert status["source"] == "github_atom_fallback"
    assert status["api_retry_at"] > int(main.time.time())
    assert status["last_error"] is None
    assert any(url == main.UPDATE_RELEASE_FEED for url, _headers in calls)


def test_nas_release_respects_persisted_backoff_and_manual_check_cooldown(client, tmp_path, monkeypatch):
    import json
    from app import main

    update_dir = tmp_path / "updates"
    update_dir.mkdir()
    now = int(main.time.time())
    (update_dir / "status.json").write_text(json.dumps({
        "next_retry_at": now + 900,
        "manual_check_after": now + 60,
        "failure_count": 4
    }), "utf-8")

    def unexpected_urlopen(*args, **kwargs):
        pytest.fail("a request must not be sent during the persisted backoff window")

    monkeypatch.setattr("urllib.request.urlopen", unexpected_urlopen)
    with pytest.raises(main.UpdateDeferredError):
        main.refresh_app_release()
    response = client.post("/api/app-update/check")
    assert response.status_code == 200
    assert response.json()["checked"] is False
    assert "过于频繁" in response.json()["message"]


def test_web_dashboard_login_overview_and_complete_export(client):
    token = register(client, "web-dashboard@example.test")
    family_id = client.get("/api/me", headers=auth(token)).json()["families"][0]["id"]
    baby_id = str(uuid.uuid4())
    event_id = str(uuid.uuid4())
    pushed = client.post("/api/sync/push", headers=auth(token), json={"family_id": family_id, "changes": [
        {"operation_id": str(uuid.uuid4()), "entity_type": "baby", "entity_id": baby_id,
         "operation": "upsert", "payload": {"id": baby_id, "displayName": "小宝", "birthTime": 123456789}, "base_version": 0},
        {"operation_id": str(uuid.uuid4()), "entity_type": "event", "entity_id": event_id,
         "operation": "upsert", "payload": {"id": event_id, "eventType": "FEEDING", "occurredAt": 123456789,
                                             "payloadJson": "{\"amountMl\":120}"}, "base_version": 0}
    ]})
    assert pushed.status_code == 200

    # The dashboard shell is public; all personal data APIs require the account session.
    assert client.get("/").status_code == 200
    assert client.get("/api/web/overview").status_code == 401

    logged_in = client.post("/api/web/login", json={
        "email": "web-dashboard@example.test", "password": "correct-horse-battery"
    })
    assert logged_in.status_code == 200
    assert "babylog_session" in logged_in.headers.get("set-cookie", "")
    overview = client.get("/api/web/overview")
    assert overview.status_code == 200, overview.text
    assert overview.json()["counts"] == {"babies": 1, "events": 1, "photos": 0}
    assert overview.json()["babies"][0]["payload"]["displayName"] == "小宝"

    exported = client.get("/api/web/export")
    assert exported.status_code == 200
    assert exported.headers["content-type"].startswith("application/zip")
    with zipfile.ZipFile(io.BytesIO(exported.content)) as archive:
        backup = json.loads(archive.read("backup.json"))
        assert archive.getinfo("backup.json").compress_type == zipfile.ZIP_DEFLATED
    assert backup["format"] == "babylog-backup"
    assert {item["entity_type"] for item in backup["records"]} == {"baby", "event"}
    assert len(backup["records"]) == 2




def test_web_export_can_exclude_or_include_photo_assets(client):
    token = register(client, "web-export-options@example.test")
    family_id = client.get("/api/me", headers=auth(token)).json()["families"][0]["id"]
    baby_id = str(uuid.uuid4())
    photo_id = str(uuid.uuid4())
    event_id = str(uuid.uuid4())

    avatar = client.post(
        f"/api/babies/{baby_id}/avatar", headers=auth(token),
        params={"family_id": family_id},
        files={"file": ("avatar.jpg", bytes([255, 216, 255]) + b"avatar", "image/jpeg")}
    )
    assert avatar.status_code == 200, avatar.text

    photo = client.post(
        "/api/photos/upload", headers=auth(token),
        params={"family_id": family_id, "photo_id": photo_id},
        files={"file": ("photo.jpg", bytes([255, 216, 255]) + b"photo", "image/jpeg")}
    )
    assert photo.status_code == 200, photo.text

    pushed = client.post("/api/sync/push", headers=auth(token), json={"family_id": family_id, "changes": [
        {"operation_id": str(uuid.uuid4()), "entity_type": "baby", "entity_id": baby_id,
         "operation": "upsert", "payload": {"id": baby_id, "displayName": "小宝", "hasAvatar": True}, "base_version": 0},
        {"operation_id": str(uuid.uuid4()), "entity_type": "event", "entity_id": event_id,
         "operation": "upsert", "payload": {"id": event_id, "eventType": "FEEDING", "amountMl": 120}, "base_version": 0},
        {"operation_id": str(uuid.uuid4()), "entity_type": "photo", "entity_id": photo_id,
         "operation": "upsert", "payload": {"id": photo_id, "babyId": baby_id, "caption": "满月",
                                             "takenAt": 123456789, "createdAt": 123456789,
                                             "updatedAt": 123456789, "mimeType": "image/jpeg"}, "base_version": 0},
    ]})
    assert pushed.status_code == 200, pushed.text

    login = client.post("/api/web/login", json={
        "email": "web-export-options@example.test", "password": "correct-horse-battery"
    })
    assert login.status_code == 200

    data_only = client.get("/api/web/export")
    assert data_only.status_code == 200
    with zipfile.ZipFile(io.BytesIO(data_only.content)) as archive:
        manifest = json.loads(archive.read("backup.json"))
        assert {item["entity_type"] for item in manifest["records"]} == {"baby", "event"}
        baby = next(item for item in manifest["records"] if item["entity_type"] == "baby")
        assert baby["payload"]["hasAvatar"] is False
        assert set(archive.namelist()) == {"backup.json"}

    complete = client.get("/api/web/export?include_photos=true")
    assert complete.status_code == 200
    with zipfile.ZipFile(io.BytesIO(complete.content)) as archive:
        manifest = json.loads(archive.read("backup.json"))
        assert {item["entity_type"] for item in manifest["records"]} == {"baby", "event", "photo"}
        assert "photos/" + photo_id + ".jpg" in archive.namelist()
        assert "avatars/" + baby_id + ".jpg" in archive.namelist()

def test_web_dashboard_pagination_date_filters_and_safe_import(client):
    source_token = register(client, "web-import-source@example.test")
    family_id = client.get("/api/me", headers=auth(source_token)).json()["families"][0]["id"]
    baby_id = str(uuid.uuid4())
    changes = [{"operation_id": str(uuid.uuid4()), "entity_type": "baby", "entity_id": baby_id,
                "operation": "upsert", "payload": {"id": baby_id, "displayName": "来源宝宝", "hasAvatar": True}, "base_version": 0}]
    avatar_bytes = b"\xff\xd8\xffbaby-avatar-image"
    avatar_upload = client.post(f"/api/babies/{baby_id}/avatar?family_id={family_id}", headers=auth(source_token), files={"file": ("avatar.jpg", avatar_bytes, "image/jpeg")})
    assert avatar_upload.status_code == 200
    event_ids = []
    for stamp in (1000, 2000, 3000):
        event_id = str(uuid.uuid4()); event_ids.append(event_id)
        changes.append({"operation_id": str(uuid.uuid4()), "entity_type": "event", "entity_id": event_id,
            "operation": "upsert", "payload": {"id": event_id, "eventType": "FEEDING", "occurredAt": stamp,
                "payloadJson": '{"amountMl":120}'}, "base_version": 0})
    photo_id = str(uuid.uuid4())
    photo_bytes = b"\xff\xd8\xffbaby-photo-image"
    photo_upload = client.post(f"/api/photos/upload?family_id={family_id}&photo_id={photo_id}", headers=auth(source_token), files={"file": ("photo.jpg", photo_bytes, "image/jpeg")})
    assert photo_upload.status_code == 200
    changes.append({"operation_id": str(uuid.uuid4()), "entity_type": "photo", "entity_id": photo_id,
        "operation": "upsert", "payload": {"id": photo_id, "takenAt": 2500, "caption": "导入测试照片"}, "base_version": 0})
    pushed = client.post("/api/sync/push", headers=auth(source_token), json={"family_id": family_id, "changes": changes})
    assert pushed.status_code == 200
    source_login = client.post("/api/web/login", json={"email": "web-import-source@example.test", "password": "correct-horse-battery"})
    assert source_login.status_code == 200
    exported = client.get("/api/web/export?include_photos=true")
    assert exported.status_code == 200

    target_token = register(client, "web-import-target@example.test")
    target_login = client.post("/api/web/login", json={"email": "web-import-target@example.test", "password": "correct-horse-battery"})
    assert target_login.status_code == 200
    preview = client.post("/api/web/import/preview", headers={"Origin": "http://testserver", "Content-Type": "application/zip"}, content=exported.content)
    assert preview.status_code == 200, preview.text
    assert preview.json()["new_count"] == 5
    assert preview.json()["photo_count"] == 1
    imported = client.post("/api/web/import", headers={"Origin": "http://testserver", "Content-Type": "application/zip"}, content=exported.content)
    assert imported.status_code == 200, imported.text
    assert imported.json() == {"imported": 5, "skipped_existing": 0}
    target_family = client.get("/api/me", headers=auth(target_token)).json()["families"][0]["id"]
    target_overview = client.get("/api/web/overview?photo_limit=10")
    imported_photo_id = target_overview.json()["photos"][0]["id"]
    assert imported_photo_id != photo_id
    restored_photo = client.get(f"/api/photos/{imported_photo_id}?family_id={target_family}")
    assert restored_photo.status_code == 200 and restored_photo.content == photo_bytes
    restored_avatar = client.get(f"/api/babies/{baby_id}/avatar?family_id={target_family}")
    assert restored_avatar.status_code == 200 and restored_avatar.content == avatar_bytes
    repeated = client.post("/api/web/import", headers={"Origin": "http://testserver", "Content-Type": "application/zip"}, content=exported.content)
    assert repeated.status_code == 200
    assert repeated.json() == {"imported": 0, "skipped_existing": 5}
    overview = client.get("/api/web/overview?event_limit=1&event_offset=1")
    assert overview.status_code == 200
    assert overview.json()["pagination"]["events"]["total"] == 3
    assert len(overview.json()["events"]) == 1
    filtered = client.get("/api/web/overview?event_from=1500&event_to=2500&event_limit=10")
    assert filtered.status_code == 200
    assert [item["payload"]["occurredAt"] for item in filtered.json()["events"]] == [2000]
    assert filtered.json()["counts"]["events"] == 3

def test_web_import_rejects_invalid_archive_without_changing_existing_data(client):
    token = register(client, "web-import-invalid@example.test")
    client.post("/api/web/login", json={"email": "web-import-invalid@example.test", "password": "correct-horse-battery"})
    invalid = client.post("/api/web/import", headers={"Origin": "http://testserver", "Content-Type": "application/zip"}, content=b"not a zip")
    assert invalid.status_code == 400
    overview = client.get("/api/web/overview")
    assert overview.status_code == 200
    assert overview.json()["counts"] == {"babies": 0, "events": 0, "photos": 0}


def test_web_import_rejects_empty_raw_body_and_oversized_content_length(client):
    register(client, "web-import-limits@example.test")
    client.post("/api/web/login", json={"email": "web-import-limits@example.test", "password": "correct-horse-battery"})
    headers = {"Origin": "http://testserver", "Content-Type": "application/zip"}
    empty = client.post("/api/web/import/preview", headers=headers, content=b"")
    assert empty.status_code == 400
    oversized = client.post("/api/web/import/preview", headers={**headers, "Content-Length": str(512 * 1024 * 1024 + 1)}, content=b"x")
    assert oversized.status_code == 413

def test_web_cookie_auth_rejects_cross_origin_mutations(client):
    register(client, "web-csrf@example.test")
    login = client.post("/api/web/login", json={
        "email": "web-csrf@example.test", "password": "correct-horse-battery"
    })
    assert login.status_code == 200
    denied = client.post("/api/families", headers={"Origin": "https://attacker.example"},
                         json={"name": "不应创建"})
    assert denied.status_code == 403
    allowed = client.post("/api/families", headers={"Origin": "http://testserver"},
                          json={"name": "我的家庭"})
    assert allowed.status_code == 200



def test_web_statistics_and_all_event_categories_are_visible(client):
    import time

    token = register(client, "web-statistics@example.test")
    family_id = client.get("/api/me", headers=auth(token)).json()["families"][0]["id"]
    now = int(time.time() * 1000)
    samples = [
        ("FEEDING", {"method": "配方奶", "amountMl": 120}),
        ("DIAPER", {"urine": True, "stool": False}),
        ("SLEEP", {"durationMinutes": 90}),
        ("GROWTH", {"weightGrams": 6500, "lengthCm": 62.5, "headCircumferenceCm": 41}),
        ("SOLID_FOOD", {"foodName": "米粉", "amount": 10, "unit": "g", "eatingStatus": "正常"}),
        ("SUPPLEMENT", {"supplementName": "维生素D3", "amount": 1, "unit": "滴"}),
        ("VACCINE", {"vaccineName": "乙肝疫苗", "dose": "第 1 剂", "clinic": "社区门诊"}),
    ]
    changes = []
    for event_type, details in samples + [("FEEDING", {"amountMl": 200})]:
        event_id = str(uuid.uuid4())
        occurred_at = now if details.get("amountMl") != 200 else now - 100 * 86400000
        changes.append({
            "operation_id": str(uuid.uuid4()),
            "entity_type": "event",
            "entity_id": event_id,
            "operation": "upsert",
            "payload": {
                "id": event_id,
                "eventType": event_type,
                "occurredAt": occurred_at,
                "payloadJson": json.dumps(details, ensure_ascii=False),
            },
            "base_version": 0,
        })

    pushed = client.post(
        "/api/sync/push",
        headers=auth(token),
        json={"family_id": family_id, "changes": changes},
    )
    assert pushed.status_code == 200, pushed.text

    overview = client.get("/api/web/overview?event_limit=20", headers=auth(token))
    assert overview.status_code == 200
    assert overview.json()["counts"]["events"] == len(samples) + 1
    assert {item["payload"]["eventType"] for item in overview.json()["events"]} == {
        event_type for event_type, _details in samples
    }
    assert next(item for item in overview.json()["events"]
                if item["payload"]["eventType"] == "SUPPLEMENT")["details"]["supplementName"] == "维生素D3"

    stats = client.get("/api/web/statistics?days=7", headers=auth(token))
    assert stats.status_code == 200, stats.text
    body = stats.json()
    assert body["days"] == 7
    assert body["totals"]["feeding_count"] == 1
    assert body["totals"]["milk_ml"] == 120
    assert body["totals"]["sleep_minutes"] == 90
    assert body["totals"]["solid_food_count"] == 1
    assert body["totals"]["supplement_count"] == 1
    assert body["totals"]["diaper_count"] == 1
    assert body["totals"]["vaccine_count"] == 1
    assert body["totals"]["growth_count"] == 1
    assert body["type_counts"]["SOLID_FOOD"] == 1
    assert body["type_counts"]["SUPPLEMENT"] == 1
    assert len(body["daily"]) == 7
    assert "no-store" in stats.headers["Cache-Control"]
    assert sum(day["milk_ml"] for day in body["daily"]) == 120
    assert body["growth"][-1]["weight_kg"] == 6.5
    assert body["growth"][-1]["length_cm"] == 62.5

    assert client.get("/api/web/statistics?days=14", headers=auth(token)).status_code == 422
    assert client.get("/api/web/statistics?days=30").status_code == 401


def test_web_page_uses_app_icon_and_exposes_statistics_tab(client):
    page = client.get("/")
    assert page.status_code == 200
    assert "/static/babylog-icon.svg" in page.text
    assert "统计报表" in page.text
    icon = client.get("/static/babylog-icon.svg")
    assert icon.status_code == 200
    assert "<svg" in icon.text


def test_web_import_rolls_back_database_and_new_media_when_later_media_collides(client):
    source_token = register(client, "web-import-rollback-source@example.test")
    source_family = client.get("/api/me", headers=auth(source_token)).json()["families"][0]["id"]
    baby_one = "00000000-0000-0000-0000-000000000001"
    baby_two = "00000000-0000-0000-0000-000000000002"

    for baby_id, content in ((baby_one, b"\xff\xd8\xfffirst-avatar"), (baby_two, b"\xff\xd8\xffsecond-avatar")):
        uploaded = client.post(
            f"/api/babies/{baby_id}/avatar?family_id={source_family}",
            headers=auth(source_token),
            files={"file": ("avatar.jpg", content, "image/jpeg")},
        )
        assert uploaded.status_code == 200, uploaded.text

    pushed = client.post("/api/sync/push", headers=auth(source_token), json={
        "family_id": source_family,
        "changes": [
            {"operation_id": str(uuid.uuid4()), "entity_type": "baby", "entity_id": baby_id,
             "operation": "upsert", "payload": {"id": baby_id, "displayName": baby_id, "hasAvatar": True},
             "base_version": 0}
            for baby_id in (baby_one, baby_two)
        ],
    })
    assert pushed.status_code == 200, pushed.text
    assert client.post("/api/web/login", json={
        "email": "web-import-rollback-source@example.test", "password": "correct-horse-battery"
    }).status_code == 200
    exported = client.get("/api/web/export?include_photos=true")
    assert exported.status_code == 200

    target_token = register(client, "web-import-rollback-target@example.test")
    target_family = client.get("/api/me", headers=auth(target_token)).json()["families"][0]["id"]
    import app.main as main
    target_folder = main.AVATAR_DIR / target_family
    target_folder.mkdir(parents=True, exist_ok=True)
    protected_file = target_folder / f"{baby_two}.jpg"
    protected_file.write_bytes(b"pre-existing-user-file")

    assert client.post("/api/web/login", json={
        "email": "web-import-rollback-target@example.test", "password": "correct-horse-battery"
    }).status_code == 200
    imported = client.post(
        "/api/web/import",
        headers={"Origin": "http://testserver", "Content-Type": "application/zip"},
        content=exported.content,
    )
    assert imported.status_code == 409
    overview = client.get("/api/web/overview")
    assert overview.status_code == 200
    assert overview.json()["counts"] == {"babies": 0, "events": 0, "photos": 0}
    assert not (target_folder / f"{baby_one}.jpg").exists()
    assert protected_file.read_bytes() == b"pre-existing-user-file"



def test_web_import_keeps_v1_data_only_backup_compatible_when_optional_fields_are_absent(client):
    token = register(client, "web-import-v1-compat@example.test")
    assert client.post("/api/web/login", json={
        "email": "web-import-v1-compat@example.test", "password": "correct-horse-battery"
    }).status_code == 200

    baby_id = str(uuid.uuid4())
    event_id = str(uuid.uuid4())
    # Early v1 backups may omit server bookkeeping fields. Data-only exports
    # also intentionally omit avatar files and clear hasAvatar on the manifest.
    manifest = {
        "format": "babylog-backup",
        "format_version": 1,
        "exported_at": 1,
        "family_name": "旧设备家庭",
        "records": [
            {
                "entity_type": "baby",
                "entity_id": baby_id,
                "payload": {"id": baby_id, "displayName": "旧备份宝宝", "hasAvatar": True},
            },
            {
                "entity_type": "event",
                "entity_id": event_id,
                "payload": {
                    "id": event_id,
                    "eventType": "FEEDING",
                    "occurredAt": 123456789,
                    "payloadJson": json.dumps({"amountMl": 120}),
                },
            },
        ],
    }
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("backup.json", json.dumps(manifest, ensure_ascii=False))

    headers = {"Origin": "http://testserver", "Content-Type": "application/zip"}
    imported = client.post("/api/web/import", headers=headers, content=buffer.getvalue())
    assert imported.status_code == 200, imported.text
    assert imported.json() == {"imported": 2, "skipped_existing": 0}

    overview = client.get("/api/web/overview?event_limit=10")
    assert overview.status_code == 200
    assert overview.json()["counts"] == {"babies": 1, "events": 1, "photos": 0}
    baby = overview.json()["babies"][0]["payload"]
    assert baby["id"] == baby_id
    assert baby["displayName"] == "旧备份宝宝"
    assert baby["hasAvatar"] is False
    event = overview.json()["events"][0]["payload"]
    assert event["id"] == event_id
    assert event["eventType"] == "FEEDING"
    assert json.loads(event["payloadJson"])["amountMl"] == 120

    repeated = client.post("/api/web/import", headers=headers, content=buffer.getvalue())
    assert repeated.status_code == 200
    assert repeated.json() == {"imported": 0, "skipped_existing": 2}


def test_web_import_rejects_unsupported_backup_version_without_mutating_data(client):
    register(client, "web-import-future-version@example.test")
    assert client.post("/api/web/login", json={
        "email": "web-import-future-version@example.test", "password": "correct-horse-battery"
    }).status_code == 200

    manifest = {
        "format": "babylog-backup",
        "format_version": 999,
        "records": [],
    }
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("backup.json", json.dumps(manifest))

    response = client.post(
        "/api/web/import",
        headers={"Origin": "http://testserver", "Content-Type": "application/zip"},
        content=buffer.getvalue(),
    )
    assert response.status_code == 400
    overview = client.get("/api/web/overview")
    assert overview.status_code == 200
    assert overview.json()["counts"] == {"babies": 0, "events": 0, "photos": 0}

@pytest.mark.parametrize("snapshot_mode", ["omitted", "explicit_delete"])
def test_authoritative_snapshot_removes_deleted_photo_file_only_after_commit(client, snapshot_mode):
    from app import main as server_main

    token = register(client, f"snapshot-photo-{snapshot_mode}@example.test")
    family_id = client.post(
        "/api/families", headers=auth(token), json={"name": "snapshot photo"}
    ).json()["family_id"]
    photo_id = str(uuid.uuid4())
    uploaded = client.post(
        "/api/photos/upload",
        headers=auth(token),
        params={"family_id": family_id, "photo_id": photo_id},
        files={"file": ("photo.jpg", b"\xff\xd8\xffphoto-data", "image/jpeg")},
    )
    assert uploaded.status_code == 200, uploaded.text

    pushed = client.post("/api/sync/push", headers=auth(token), json={
        "family_id": family_id,
        "changes": [{
            "operation_id": str(uuid.uuid4()),
            "entity_type": "photo",
            "entity_id": photo_id,
            "operation": "upsert",
            "payload": {"id": photo_id, "takenAt": 123456789},
            "base_version": 0,
        }],
    })
    assert pushed.status_code == 200
    photo_path = server_main.PHOTO_DIR / family_id / f"{photo_id}.jpg"
    assert photo_path.is_file()

    items = []
    if snapshot_mode == "explicit_delete":
        items = [{
            "operation_id": str(uuid.uuid4()),
            "entity_type": "photo",
            "entity_id": photo_id,
            "operation": "delete",
            "payload": {"id": photo_id},
        }]

    replaced = client.post("/api/sync/authoritative-snapshot", headers=auth(token), json={
        "family_id": family_id,
        "items": items,
    })
    assert replaced.status_code == 200, replaced.text
    assert not photo_path.exists()
    assert client.get(
        f"/api/photos/{photo_id}", headers=auth(token), params={"family_id": family_id}
    ).status_code == 404


@pytest.mark.parametrize("snapshot_mode", ["omitted", "explicit_delete"])
def test_authoritative_snapshot_removes_deleted_avatar_file(client, snapshot_mode):
    from app import main as server_main

    token = register(client, f"snapshot-avatar-{snapshot_mode}@example.test")
    family_id = client.post(
        "/api/families", headers=auth(token), json={"name": "snapshot avatar"}
    ).json()["family_id"]
    baby_id = str(uuid.uuid4())
    uploaded = client.post(
        f"/api/babies/{baby_id}/avatar",
        headers=auth(token),
        params={"family_id": family_id},
        files={"file": ("avatar.jpg", b"\xff\xd8\xffavatar-data", "image/jpeg")},
    )
    assert uploaded.status_code == 200, uploaded.text

    pushed = client.post("/api/sync/push", headers=auth(token), json={
        "family_id": family_id,
        "changes": [{
            "operation_id": str(uuid.uuid4()),
            "entity_type": "baby",
            "entity_id": baby_id,
            "operation": "upsert",
            "payload": {"id": baby_id, "displayName": "宝宝", "hasAvatar": True},
            "base_version": 0,
        }],
    })
    assert pushed.status_code == 200
    avatar_path = server_main.AVATAR_DIR / family_id / f"{baby_id}.jpg"
    assert avatar_path.is_file()

    items = []
    if snapshot_mode == "explicit_delete":
        items = [{
            "operation_id": str(uuid.uuid4()),
            "entity_type": "baby",
            "entity_id": baby_id,
            "operation": "delete",
            "payload": {"id": baby_id},
        }]

    replaced = client.post("/api/sync/authoritative-snapshot", headers=auth(token), json={
        "family_id": family_id,
        "items": items,
    })
    assert replaced.status_code == 200, replaced.text
    assert not avatar_path.exists()
    assert client.get(
        f"/api/babies/{baby_id}/avatar",
        headers=auth(token),
        params={"family_id": family_id},
    ).status_code == 404


def test_sync_push_deleting_baby_removes_nas_avatar_after_commit(client):
    from app import main as server_main

    token = register(client, "sync-delete-avatar@example.test")
    family_id = client.get("/api/me", headers=auth(token)).json()["families"][0]["id"]
    baby_id = str(uuid.uuid4())
    uploaded = client.post(
        f"/api/babies/{baby_id}/avatar?family_id={family_id}",
        headers=auth(token),
        files={"file": ("avatar.jpg", b"\xff\xd8\xffsynced-avatar", "image/jpeg")},
    )
    assert uploaded.status_code == 200, uploaded.text

    created = client.post("/api/sync/push", headers=auth(token), json={
        "family_id": family_id,
        "changes": [{
            "operation_id": str(uuid.uuid4()),
            "entity_type": "baby",
            "entity_id": baby_id,
            "operation": "upsert",
            "payload": {"id": baby_id, "displayName": "宝宝", "hasAvatar": True},
            "base_version": 0,
        }],
    })
    assert created.status_code == 200, created.text
    avatar_path = server_main.AVATAR_DIR / family_id / f"{baby_id}.jpg"
    assert avatar_path.is_file()

    deleted = client.post("/api/sync/push", headers=auth(token), json={
        "family_id": family_id,
        "changes": [{
            "operation_id": str(uuid.uuid4()),
            "entity_type": "baby",
            "entity_id": baby_id,
            "operation": "delete",
            "payload": {"id": baby_id},
            "base_version": 1,
        }],
    })
    assert deleted.status_code == 200, deleted.text
    assert deleted.json()["accepted"]
    assert not avatar_path.exists()
    assert client.get(
        f"/api/babies/{baby_id}/avatar",
        headers=auth(token),
        params={"family_id": family_id},
    ).status_code == 404


def test_web_import_cleans_partial_media_temp_file_after_interrupted_copy(client, monkeypatch):
    token = register(client, "web-import-partial-copy@example.test")
    family_id = client.get("/api/me", headers=auth(token)).json()["families"][0]["id"]
    baby_id = str(uuid.uuid4())
    manifest = {
        "format": "babylog-backup",
        "format_version": 1,
        "exported_at": 1,
        "family_name": "测试家庭",
        "records": [{
            "entity_type": "baby",
            "entity_id": baby_id,
            "payload": {"id": baby_id, "displayName": "测试宝宝", "hasAvatar": True},
            "version": 1,
            "updated_at": 1,
            "asset_path": f"avatars/{baby_id}.jpg",
        }],
    }
    archive_bytes = io.BytesIO()
    with zipfile.ZipFile(archive_bytes, "w") as archive:
        archive.writestr("backup.json", json.dumps(manifest))
        archive.writestr(f"avatars/{baby_id}.jpg", b"\\xff\\xd8\\xffavatar-content")

    import app.main as main

    def interrupted_copy(source, output, length=0):
        output.write(b"partial-avatar")
        raise OSError("simulated interrupted media copy")

    monkeypatch.setattr(main.shutil, "copyfileobj", interrupted_copy)
    with pytest.raises(OSError, match="simulated interrupted media copy"):
        client.post(
            "/api/web/import",
            headers={"Origin": "http://testserver", "Content-Type": "application/zip"},
            content=archive_bytes.getvalue(),
        )

    avatar_folder = main.AVATAR_DIR / family_id
    assert not (avatar_folder / f"{baby_id}.jpg").exists()
    assert not list(avatar_folder.glob("*.import"))
    overview = client.get("/api/web/overview", headers=auth(token))
    assert overview.status_code == 200
    assert overview.json()["counts"] == {"babies": 0, "events": 0, "photos": 0}
