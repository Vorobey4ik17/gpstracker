"""FastVPN сервер: геоточки + live-карта (до 50+ устройств)."""

import sqlite3
import time
from pathlib import Path

from flask import Flask, jsonify, request, send_file
from flask_socketio import SocketIO

BASE = Path(__file__).parent
DB = BASE / "gps.db"

app = Flask(__name__)
socketio = SocketIO(app, cors_allowed_origins="*")


def get_db():
    con = sqlite3.connect(DB)
    con.row_factory = sqlite3.Row
    return con


def init_db():
    con = get_db()
    con.execute("""create table if not exists locations(
        id integer primary key autoincrement, device_id text not null,
        device_model text, lat real not null, lon real not null,
        accuracy real, speed real, created_at real not null)""")
    con.execute("create index if not exists idx_dev_time on locations(device_id, created_at)")
    con.commit()
    con.close()


def kalman_1d(vals, ts):
    if len(vals) < 3:
        return vals
    pos, vel = vals[0], 0.0
    p11, p12, p21, p22 = 10.0, 0.0, 0.0, 10.0
    out = [vals[0]]
    for i in range(1, len(vals)):
        dt = max(1.0, ts[i] - ts[i - 1])
        pp11 = p11 + dt * (p12 + p21) + dt * dt * p22 + 1e-7
        pp12 = p12 + dt * p22
        pp21 = p21 + dt * p22
        pp22 = p22 + 1e-7
        z = vals[i]
        S = pp11 + 1e-7
        k1, k2 = pp11 / S, pp21 / S
        pos, vel = pos + vel * dt + k1 * (z - pos - vel * dt), vel + k2 * (z - pos - vel * dt)
        p11, p12 = pp11 - k1 * pp11, pp12 - k1 * pp12
        p21, p22 = pp21 - k2 * pp11, pp22 - k2 * pp12
        out.append(pos)
    return out


def smooth(points):
    if len(points) < 3:
        return points
    ts = [p["created_at"] for p in points]
    slat = kalman_1d([p["lat"] for p in points], ts)
    slon = kalman_1d([p["lon"] for p in points], ts)
    for p, a, b in zip(points, slat, slon):
        p["lat"], p["lon"] = a, b
    return points


@app.route("/")
def index():
    return send_file(BASE / "dashboard.html")


@app.route("/points", methods=["POST"])
def points():
    rows = request.get_json(force=True)
    if isinstance(rows, dict):
        rows = [rows]
    con = get_db()
    now = time.time()
    for r in rows:
        row = {"device_id": str(r.get("device_id", "unknown")),
               "device_model": str(r.get("device_model", "")),
               "lat": float(r["lat"]), "lon": float(r["lon"]),
               "accuracy": r.get("accuracy"), "speed": r.get("speed"),
               "created_at": now}
        con.execute("insert into locations(device_id,device_model,lat,lon,accuracy,speed,created_at)"
                    " values(?,?,?,?,?,?,?)",
                    (row["device_id"], row["device_model"], row["lat"], row["lon"],
                     row["accuracy"], row["speed"], row["created_at"]))
        socketio.emit("point", row)
    con.commit()
    con.close()
    return jsonify(ok=True, received=len(rows))


@app.route("/history")
def history():
    dev = request.args.get("device", "device-001")
    con = get_db()
    rows = [dict(r) for r in con.execute(
        "select * from locations where device_id=? order by created_at asc limit 10000",
        (dev,)).fetchall()]
    con.close()
    return jsonify(smooth(rows))


if __name__ == "__main__":
    init_db()
    print("=" * 50)
    print("Карта диспетчера:  http://localhost:5000")
    print("Расчёт на 50+ устройств одновременно")
    print("=" * 50)
    socketio.run(app, host="0.0.0.0", port=5000, allow_unsafe_werkzeug=True)
