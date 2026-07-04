// 최초 기동 시 1회 실행 (docker-entrypoint-initdb.d).
// 루트 계정과 분리된 앱 전용 유저를 vpnlab DB에 생성한다.
// 앱(control-plane)은 이 최소 권한 계정(readWrite)만 사용 — 루트 자격증명 노출 방지.
const appUser = process.env.MONGO_APP_USER;
const appPass = process.env.MONGO_APP_PASS;

db = db.getSiblingDB('vpnlab');

if (!db.getUser(appUser)) {
  db.createUser({
    user: appUser,
    pwd: appPass,
    roles: [{ role: 'readWrite', db: 'vpnlab' }],
  });
  print(`[mongo-init] created app user '${appUser}' with readWrite on vpnlab`);
} else {
  print(`[mongo-init] app user '${appUser}' already exists — skip`);
}
