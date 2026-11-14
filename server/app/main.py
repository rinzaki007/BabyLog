import hashlib, json, os, secrets, sqlite3, time, uuid
from pathlib import Path
from typing import Any
import jwt
from fastapi import Depends, FastAPI, File, HTTPException, UploadFile
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pwdlib import PasswordHash
from pydantic import BaseModel, Field

DATA_DIR=Path(os.getenv("BABYLOG_DATA_DIR","/data")); DB_PATH=DATA_DIR/"babylog.db"; PHOTO_DIR=DATA_DIR/"photos"; AVATAR_DIR=DATA_DIR/"avatars"
JWT_SECRET=os.getenv("BABYLOG_JWT_SECRET",""); JWT_ISSUER="babylog-sync"
TOKEN_TTL=int(os.getenv("BABYLOG_TOKEN_TTL_SECONDS","2592000")); MAX_PHOTO_BYTES=int(os.getenv("BABYLOG_MAX_PHOTO_BYTES",str(20*1024*1024)))
password_hash=PasswordHash.recommended(); bearer=HTTPBearer(auto_error=False)
app=FastAPI(title="BabyLog Sync Server",docs_url=None,redoc_url=None,openapi_url=None)

class Credentials(BaseModel):
    email:str=Field(min_length=3,max_length=254); password:str=Field(min_length=10,max_length=128)
class FamilyCreate(BaseModel): name:str=Field(default="我的家庭",min_length=1,max_length=80)
class InviteJoin(BaseModel): code:str=Field(min_length=20,max_length=128)
class Change(BaseModel):
    operation_id:str; entity_type:str; entity_id:str; operation:str; payload:dict[str,Any]; base_version:int|None=None
class PushRequest(BaseModel): family_id:str; changes:list[Change]=Field(max_length=200)
class SnapshotRequest(BaseModel):
    family_id:str
    items:list[Change]=Field(max_length=10000)

def connect():
    DATA_DIR.mkdir(parents=True,exist_ok=True); PHOTO_DIR.mkdir(parents=True,exist_ok=True)
    db=sqlite3.connect(DB_PATH,timeout=30); db.row_factory=sqlite3.Row
    db.execute("PRAGMA journal_mode=WAL"); db.execute("PRAGMA foreign_keys=ON"); return db

def init_db():
    with connect() as db:
        db.executescript("""
        CREATE TABLE IF NOT EXISTS users(id TEXT PRIMARY KEY,email TEXT NOT NULL UNIQUE,password_hash TEXT NOT NULL,created_at INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS families(id TEXT PRIMARY KEY,name TEXT NOT NULL,created_at INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS family_members(family_id TEXT NOT NULL REFERENCES families(id),user_id TEXT NOT NULL REFERENCES users(id),role TEXT NOT NULL,joined_at INTEGER NOT NULL,PRIMARY KEY(family_id,user_id));
        CREATE TABLE IF NOT EXISTS invites(id TEXT PRIMARY KEY,family_id TEXT NOT NULL REFERENCES families(id),code_hash TEXT NOT NULL UNIQUE,expires_at INTEGER NOT NULL,used_at INTEGER);
        CREATE TABLE IF NOT EXISTS records(family_id TEXT NOT NULL,entity_type TEXT NOT NULL,entity_id TEXT NOT NULL,payload TEXT NOT NULL,version INTEGER NOT NULL,updated_at INTEGER NOT NULL,deleted INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(family_id,entity_type,entity_id));
        CREATE TABLE IF NOT EXISTS changes(seq INTEGER PRIMARY KEY AUTOINCREMENT,family_id TEXT NOT NULL,entity_type TEXT NOT NULL,entity_id TEXT NOT NULL,operation_id TEXT NOT NULL,payload TEXT NOT NULL,version INTEGER NOT NULL,created_at INTEGER NOT NULL,UNIQUE(family_id,operation_id));
        CREATE TABLE IF NOT EXISTS photos(id TEXT PRIMARY KEY,family_id TEXT NOT NULL,relative_path TEXT NOT NULL,mime_type TEXT NOT NULL,size_bytes INTEGER NOT NULL,created_at INTEGER NOT NULL);
        CREATE INDEX IF NOT EXISTS idx_changes_family_seq ON changes(family_id,seq);
        """)
init_db()

def issue_token(uid):
    now=int(time.time()); return jwt.encode({"sub":uid,"iss":JWT_ISSUER,"iat":now,"exp":now+TOKEN_TTL},JWT_SECRET,algorithm="HS256")
def current_user(creds:HTTPAuthorizationCredentials|None=Depends(bearer)):
    if not JWT_SECRET or len(JWT_SECRET)<32: raise HTTPException(503,"服务器未配置有效的 BABYLOG_JWT_SECRET")
    if creds is None: raise HTTPException(401,"需要登录")
    try: return jwt.decode(creds.credentials,JWT_SECRET,algorithms=["HS256"],issuer=JWT_ISSUER,options={"require":["exp","iat","sub","iss"]})["sub"]
    except jwt.PyJWTError: raise HTTPException(401,"登录凭证无效或已过期")
def require_member(db,fid,uid):
    if not db.execute("SELECT 1 FROM family_members WHERE family_id=? AND user_id=?",(fid,uid)).fetchone(): raise HTTPException(403,"无权访问该家庭")
def clean_email(email): return email.strip().lower()

@app.get("/health")
def health():
    with connect() as db: db.execute("SELECT 1").fetchone()
    return {"status":"ok","service":"babylog-sync"}
@app.post("/api/auth/register")
def register(body:Credentials):
    email=clean_email(body.email)
    if "@" not in email: raise HTTPException(422,"邮箱格式不正确")
    uid=str(uuid.uuid4())
    try:
        with connect() as db: db.execute("INSERT INTO users VALUES (?,?,?,?)",(uid,email,password_hash.hash(body.password),int(time.time())))
    except sqlite3.IntegrityError: raise HTTPException(409,"该邮箱已注册")
    return {"access_token":issue_token(uid),"token_type":"bearer","user_id":uid}
@app.post("/api/auth/login")
def login(body:Credentials):
    with connect() as db: user=db.execute("SELECT * FROM users WHERE email=?",(clean_email(body.email),)).fetchone()
    if not user or not password_hash.verify(body.password,user["password_hash"]): raise HTTPException(401,"邮箱或密码错误")
    return {"access_token":issue_token(user["id"]),"token_type":"bearer","user_id":user["id"]}
@app.get("/api/me")
def me(uid:str=Depends(current_user)):
    with connect() as db: rows=db.execute("SELECT f.id,f.name,m.role FROM families f JOIN family_members m ON m.family_id=f.id WHERE m.user_id=?",(uid,)).fetchall()
    return {"user_id":uid,"families":[dict(r) for r in rows]}
@app.post("/api/families")
def create_family(body:FamilyCreate,uid:str=Depends(current_user)):
    fid=str(uuid.uuid4()); now=int(time.time())
    with connect() as db:
        db.execute("INSERT INTO families VALUES (?,?,?)",(fid,body.name,now)); db.execute("INSERT INTO family_members VALUES (?,?,?,?)",(fid,uid,"owner",now))
    return {"family_id":fid,"name":body.name}
@app.post("/api/families/{fid}/invites")
def create_invite(fid:str,uid:str=Depends(current_user)):
    code=secrets.token_urlsafe(32); now=int(time.time())
    with connect() as db:
        require_member(db,fid,uid); member=db.execute("SELECT role FROM family_members WHERE family_id=? AND user_id=?",(fid,uid)).fetchone()
        if member["role"]!="owner": raise HTTPException(403,"只有家庭创建者可以邀请成员")
        db.execute("INSERT INTO invites VALUES (?,?,?,?,NULL)",(str(uuid.uuid4()),fid,hashlib.sha256(code.encode()).hexdigest(),now+72*3600))
    return {"invite_code":code,"expires_at":now+72*3600}
@app.post("/api/families/join")
def join_family(body:InviteJoin,uid:str=Depends(current_user)):
    now=int(time.time()); digest=hashlib.sha256(body.code.encode()).hexdigest()
    with connect() as db:
        invite=db.execute("SELECT * FROM invites WHERE code_hash=?",(digest,)).fetchone()
        if not invite or invite["used_at"] is not None or invite["expires_at"]<now: raise HTTPException(400,"邀请码无效、已使用或已过期")
        db.execute("INSERT OR IGNORE INTO family_members VALUES (?,?,?,?)",(invite["family_id"],uid,"member",now)); db.execute("UPDATE invites SET used_at=? WHERE id=?",(now,invite["id"]))
    return {"family_id":invite["family_id"],"joined":True}
@app.post("/api/sync/push")
def push(body:PushRequest,uid:str=Depends(current_user)):
    accepted=[]; conflicts=[]; allowed={"baby","event","photo"}; photo_files_to_delete=[]
    with connect() as db:
        require_member(db,body.family_id,uid); db.execute("BEGIN IMMEDIATE")
        for c in body.changes:
            if c.entity_type not in allowed or c.operation not in {"upsert","delete"}:
                conflicts.append({"operation_id":c.operation_id,"reason":"unsupported_operation"}); continue
            prior=db.execute("SELECT version FROM records WHERE family_id=? AND entity_type=? AND entity_id=?",(body.family_id,c.entity_type,c.entity_id)).fetchone()
            duplicate=db.execute("SELECT seq FROM changes WHERE family_id=? AND operation_id=?",(body.family_id,c.operation_id)).fetchone()
            if duplicate:
                current=db.execute("SELECT version FROM records WHERE family_id=? AND entity_type=? AND entity_id=?",(body.family_id,c.entity_type,c.entity_id)).fetchone()
                accepted.append({"operation_id":c.operation_id,"duplicate":True,"version":current["version"] if current else 0})
                continue
            version=prior["version"] if prior else 0
            if c.base_version is not None and c.base_version!=version:
                conflicts.append({"operation_id":c.operation_id,"entity_id":c.entity_id,"server_version":version,"reason":"version_conflict"}); continue
            new_version=version+1; now=int(time.time()); payload=json.dumps(c.payload,ensure_ascii=False,separators=(",",":"))
            db.execute("""INSERT INTO records VALUES (?,?,?,?,?,?,?) ON CONFLICT(family_id,entity_type,entity_id) DO UPDATE SET payload=excluded.payload,version=excluded.version,updated_at=excluded.updated_at,deleted=excluded.deleted""",(body.family_id,c.entity_type,c.entity_id,payload,new_version,now,int(c.operation=="delete")))
            cur=db.execute("INSERT INTO changes(family_id,entity_type,entity_id,operation_id,payload,version,created_at) VALUES (?,?,?,?,?,?,?)",(body.family_id,c.entity_type,c.entity_id,c.operation_id,json.dumps({"operation":c.operation,"payload":c.payload},ensure_ascii=False),new_version,now))
            if c.entity_type=="photo" and c.operation=="delete":
                photo=db.execute("SELECT relative_path FROM photos WHERE id=? AND family_id=?",(c.entity_id,body.family_id)).fetchone()
                if photo: photo_files_to_delete.append(PHOTO_DIR/photo["relative_path"])
                db.execute("DELETE FROM photos WHERE id=? AND family_id=?",(c.entity_id,body.family_id))
            accepted.append({"operation_id":c.operation_id,"version":new_version,"seq":cur.lastrowid})
    for photo_path in photo_files_to_delete:
        resolved=photo_path.resolve()
        if PHOTO_DIR.resolve() in resolved.parents:
            resolved.unlink(missing_ok=True)
    return {"accepted":accepted,"conflicts":conflicts}

@app.post("/api/sync/authoritative-snapshot")
def authoritative_snapshot(body:SnapshotRequest,uid:str=Depends(current_user)):
    """Replace family business data with a complete snapshot from the family owner."""
    allowed={"baby","event","photo"}
    seen=set()
    for item in body.items:
        key=(item.entity_type,item.entity_id)
        if item.entity_type not in allowed or item.operation not in {"upsert","delete"}:
            raise HTTPException(422,"快照包含不支持的数据类型或操作")
        if key in seen:
            raise HTTPException(422,"快照中存在重复数据")
        seen.add(key)
    now=int(time.time())
    with connect() as db:
        require_member(db,body.family_id,uid)
        member=db.execute("SELECT role FROM family_members WHERE family_id=? AND user_id=?",(body.family_id,uid)).fetchone()
        if not member or member["role"]!="owner":
            raise HTTPException(403,"只有家庭创建者可以覆盖家庭数据")
        db.execute("BEGIN IMMEDIATE")
        existing=db.execute("SELECT entity_type,entity_id,version FROM records WHERE family_id=? AND entity_type IN ('baby','event','photo')",(body.family_id,)).fetchall()
        old={(r["entity_type"],r["entity_id"]):r["version"] for r in existing}
        # Validate all external media before changing any database state.
        for item in body.items:
            if item.operation=="upsert" and item.entity_type=="photo":
                photo=db.execute("SELECT relative_path FROM photos WHERE id=? AND family_id=?",(item.entity_id,body.family_id)).fetchone()
                if not photo:
                    raise HTTPException(409,f"照片尚未上传完成：{item.entity_id}")
                photo_path=(PHOTO_DIR/photo["relative_path"]).resolve()
                if PHOTO_DIR.resolve() not in photo_path.parents or not photo_path.is_file():
                    raise HTTPException(409,f"照片文件不存在：{item.entity_id}")
            if item.operation=="upsert" and item.entity_type=="baby" and item.payload.get("hasAvatar"):
                avatar=(AVATAR_DIR/str(uuid.UUID(body.family_id))/f"{uuid.UUID(item.entity_id)}.jpg").resolve()
                if AVATAR_DIR.resolve() not in avatar.parents or not avatar.is_file():
                    raise HTTPException(409,f"宝宝头像尚未上传完成：{item.entity_id}")
        snapshot_keys=set()
        changes_to_append=[]
        for item in body.items:
            key=(item.entity_type,item.entity_id); snapshot_keys.add(key)
            version=old.get(key,0)+1
            deleted=int(item.operation=="delete")
            payload=json.dumps(item.payload,ensure_ascii=False,separators=(",",":"))
            db.execute("""INSERT INTO records VALUES (?,?,?,?,?,?,?) ON CONFLICT(family_id,entity_type,entity_id) DO UPDATE SET payload=excluded.payload,version=excluded.version,updated_at=excluded.updated_at,deleted=excluded.deleted""",(body.family_id,item.entity_type,item.entity_id,payload,version,now,deleted))
            changes_to_append.append((item.entity_type,item.entity_id,item.operation,item.payload,version))
            if item.entity_type=="photo" and deleted:
                db.execute("DELETE FROM photos WHERE id=? AND family_id=?",(item.entity_id,body.family_id))
        # Any cloud-only record absent from the authoritative device is tombstoned.
        for key,old_version in old.items():
            if key in snapshot_keys: continue
            entity_type,entity_id=key; version=old_version+1; payload={"id":entity_id}
            db.execute("UPDATE records SET payload=?,version=?,updated_at=?,deleted=1 WHERE family_id=? AND entity_type=? AND entity_id=?",(json.dumps(payload),version,now,body.family_id,entity_type,entity_id))
            changes_to_append.append((entity_type,entity_id,"delete",payload,version))
            if entity_type=="photo":
                db.execute("DELETE FROM photos WHERE id=? AND family_id=?",(entity_id,body.family_id))
        for entity_type,entity_id,operation,payload,version in changes_to_append:
            operation_id="snapshot:"+str(uuid.uuid4())
            change_payload=json.dumps({"operation":operation,"payload":payload},ensure_ascii=False,separators=(",",":"))
            db.execute("INSERT INTO changes(family_id,entity_type,entity_id,operation_id,payload,version,created_at) VALUES (?,?,?,?,?,?,?)",(body.family_id,entity_type,entity_id,operation_id,change_payload,version,now))
        latest=db.execute("SELECT COALESCE(MAX(seq),0) AS cursor FROM changes WHERE family_id=?",(body.family_id,)).fetchone()["cursor"]
    return {"replaced":True,"items":len(body.items),"changes":len(changes_to_append),"cursor":latest}

@app.get("/api/sync/pull")
def pull(family_id:str,cursor:int=0,limit:int=500,uid:str=Depends(current_user)):
    limit=max(1,min(limit,1000))
    with connect() as db:
        require_member(db,family_id,uid)
        rows=db.execute(
            "SELECT seq,entity_type,entity_id,payload,version,created_at FROM changes WHERE family_id=? AND seq>? ORDER BY seq LIMIT ?",
            (family_id,max(0,cursor),limit)
        ).fetchall()
        items=[]
        for r in rows:
            p=json.loads(r["payload"])
            operation=p["operation"]
            # A fresh device/session may replay the entire change history. Do not
            # download an old photo-upsert when that photo has since been deleted:
            # its file and photos row are intentionally removed by the delete.
            if r["entity_type"]=="photo" and operation=="upsert":
                latest=db.execute(
                    "SELECT deleted FROM records WHERE family_id=? AND entity_type='photo' AND entity_id=?",
                    (family_id,r["entity_id"])
                ).fetchone()
                if latest and latest["deleted"]:
                    continue
            items.append({"cursor":r["seq"],"entity_type":r["entity_type"],"entity_id":r["entity_id"],"operation":operation,"payload":p["payload"],"version":r["version"],"created_at":r["created_at"]})
    return {
        "items":items,
        # Advance across filtered history too, otherwise clients would loop forever.
        "next_cursor":rows[-1]["seq"] if rows else cursor,
        "scanned_count":len(rows)
    }
@app.post("/api/photos/upload")
async def upload_photo(family_id:str,photo_id:str,file:UploadFile=File(...),uid:str=Depends(current_user)):
    types={"image/jpeg":".jpg","image/png":".png","image/webp":".webp","image/heic":".heic","image/heif":".heif"}; mime=file.content_type or ""
    if mime not in types: raise HTTPException(415,"仅支持 JPEG、PNG、WebP 或 HEIC 图片")
    with connect() as db: require_member(db,family_id,uid)
    folder=PHOTO_DIR/family_id; folder.mkdir(parents=True,exist_ok=True); target=folder/f"{uuid.UUID(photo_id)}{types[mime]}"; temp=target.with_suffix(target.suffix+".part"); total=0
    try:
        with temp.open("wb") as out:
            while chunk:=await file.read(1024*1024):
                total+=len(chunk)
                if total>MAX_PHOTO_BYTES: raise HTTPException(413,"照片超过服务器大小限制")
                out.write(chunk)
        temp.replace(target)
    except Exception:
        temp.unlink(missing_ok=True); raise
    rel=str(target.relative_to(PHOTO_DIR))
    with connect() as db: db.execute("INSERT OR REPLACE INTO photos VALUES (?,?,?,?,?,?)",(str(uuid.UUID(photo_id)),family_id,rel,mime,total,int(time.time())))
    return {"photo_id":photo_id,"size_bytes":total,"mime_type":mime}
@app.get("/api/photos/{photo_id}")
def download_photo(photo_id:str,family_id:str,uid:str=Depends(current_user)):
    from fastapi.responses import FileResponse
    with connect() as db:
        require_member(db,family_id,uid); row=db.execute("SELECT * FROM photos WHERE id=? AND family_id=?",(photo_id,family_id)).fetchone()
    if not row: raise HTTPException(404,"照片不存在")
    path=(PHOTO_DIR/row["relative_path"]).resolve()
    if PHOTO_DIR.resolve() not in path.parents or not path.is_file(): raise HTTPException(404,"照片文件不存在")
    return FileResponse(path,media_type=row["mime_type"])
@app.post("/api/babies/{baby_id}/avatar")
async def upload_baby_avatar(baby_id:str,family_id:str,file:UploadFile=File(...),uid:str=Depends(current_user)):
    try: safe_baby_id=str(uuid.UUID(baby_id))
    except ValueError: raise HTTPException(422,"宝宝 ID 无效")
    if file.content_type!="image/jpeg": raise HTTPException(415,"头像仅支持 JPEG 格式")
    with connect() as db: require_member(db,family_id,uid)
    data=await file.read(MAX_PHOTO_BYTES+1)
    if not data: raise HTTPException(400,"头像文件为空")
    if len(data)>MAX_PHOTO_BYTES: raise HTTPException(413,"头像超过服务器大小限制")
    folder=AVATAR_DIR/str(uuid.UUID(family_id)); folder.mkdir(parents=True,exist_ok=True)
    target=folder/f"{safe_baby_id}.jpg"; temp=target.with_suffix(".jpg.part")
    try:
        temp.write_bytes(data); temp.replace(target)
    except Exception:
        temp.unlink(missing_ok=True); raise
    return {"baby_id":safe_baby_id,"size_bytes":len(data)}

@app.get("/api/babies/{baby_id}/avatar")
def download_baby_avatar(baby_id:str,family_id:str,uid:str=Depends(current_user)):
    from fastapi.responses import FileResponse
    try: safe_baby_id=str(uuid.UUID(baby_id))
    except ValueError: raise HTTPException(422,"宝宝 ID 无效")
    with connect() as db: require_member(db,family_id,uid)
    path=(AVATAR_DIR/str(uuid.UUID(family_id))/f"{safe_baby_id}.jpg").resolve()
    if AVATAR_DIR.resolve() not in path.parents or not path.is_file(): raise HTTPException(404,"宝宝头像不存在")
    return FileResponse(path,media_type="image/jpeg")

@app.delete("/api/babies/{baby_id}/avatar")
def delete_baby_avatar(baby_id:str,family_id:str,uid:str=Depends(current_user)):
    try: safe_baby_id=str(uuid.UUID(baby_id))
    except ValueError: raise HTTPException(422,"宝宝 ID 无效")
    with connect() as db: require_member(db,family_id,uid)
    path=(AVATAR_DIR/str(uuid.UUID(family_id))/f"{safe_baby_id}.jpg").resolve()
    if AVATAR_DIR.resolve() in path.parents: path.unlink(missing_ok=True)
    return {"deleted":True}

@app.on_event("startup")
def validate_secret():
    if len(JWT_SECRET)<32: raise RuntimeError("请设置至少 32 字符的 BABYLOG_JWT_SECRET")
