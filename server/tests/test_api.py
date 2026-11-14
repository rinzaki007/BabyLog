import importlib
import os
import uuid

import pytest
from fastapi.testclient import TestClient


@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("BABYLOG_DATA_DIR", str(tmp_path))
    monkeypatch.setenv("BABYLOG_JWT_SECRET", "test-only-secret-that-is-at-least-32-bytes-long")
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
        files={"file": ("test.jpg", b"photo-bytes", "image/jpeg")}
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
        files={"file": ("old.jpg", b"old-photo-bytes", "image/jpeg")}
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
