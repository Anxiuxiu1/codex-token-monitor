import argparse
import glob
import json
import os
import socket
import threading
import time
import traceback
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse


SESSION_ROOT = os.path.expandvars(r"%USERPROFILE%\.codex\sessions")
LOG_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cache-bridge.log")
LOG_LOCK = threading.Lock()


def log_line(message):
    stamp = datetime.now().astimezone().strftime("%Y-%m-%d %H:%M:%S")
    with LOG_LOCK:
        with open(LOG_PATH, "a", encoding="utf-8") as handle:
            handle.write(f"{stamp} {message}\n")


def lan_ip():
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("8.8.8.8", 80))
        address = probe.getsockname()[0]
        if address and not address.startswith("127."):
            return address
    except OSError:
        pass
    finally:
        probe.close()
    try:
        for address in socket.gethostbyname_ex(socket.gethostname())[2]:
            if not address.startswith("127."):
                return address
    except OSError:
        pass
    return "127.0.0.1"


def discovery_loop(port):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("0.0.0.0", port))
    log_line(f"UDP discovery listening on {port}")
    while True:
        try:
            data, address = sock.recvfrom(256)
            if data.strip() != b"CODEX_CACHE_BRIDGE":
                continue
            log_line(f"UDP discovery from {address[0]}:{address[1]}")
            reply = json.dumps({
                "lan_url": f"http://{lan_ip()}:8765/cache?scope=today",
                "host_ip": lan_ip(),
            }).encode("utf-8")
            sock.sendto(reply, address)
        except OSError:
            continue


def local_midnight_ms():
    now = datetime.now().astimezone()
    start = now.replace(hour=0, minute=0, second=0, microsecond=0)
    return int(start.timestamp() * 1000)


def parse_time_ms(value):
    if not value:
        return 0
    try:
        return int(datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp() * 1000)
    except Exception:
        return 0


def collect_usage(scope="today"):
    now_ms = int(datetime.now().timestamp() * 1000)
    if scope == "7d":
        start_ms = now_ms - 7 * 24 * 60 * 60 * 1000
    elif scope == "all":
        start_ms = 0
    else:
        scope = "today"
        start_ms = local_midnight_ms()
    seen = set()
    total_input = 0
    total_cached = 0
    total_output = 0
    samples = 0
    newest = 0

    paths = glob.glob(os.path.join(SESSION_ROOT, "**", "*.jsonl"), recursive=True)
    for path in paths:
        try:
            if start_ms and os.path.getmtime(path) * 1000 < start_ms - 24 * 60 * 60 * 1000:
                continue
            with open(path, "r", encoding="utf-8") as handle:
                for line in handle:
                    if '"token_usage_record"' not in line:
                        continue
                    item = json.loads(line)
                    if item.get("type") != "token_usage_record":
                        continue
                    stamp = parse_time_ms(item.get("timestamp"))
                    if stamp < start_ms:
                        continue
                    payload = item.get("payload") or {}
                    usage = payload.get("usage") or {}
                    response_id = payload.get("response_id")
                    turn_id = payload.get("turn_id")
                    key = (response_id, turn_id)
                    if key in seen:
                        continue
                    seen.add(key)
                    total_input += int(usage.get("input_tokens") or 0)
                    total_cached += int(usage.get("cached_input_tokens") or 0)
                    total_output += int(usage.get("output_tokens") or 0)
                    samples += 1
                    newest = max(newest, stamp)
        except (OSError, UnicodeError, json.JSONDecodeError):
            continue

    rate = total_cached / total_input * 100 if total_input else 0
    return {
        "cache_hit_rate": round(rate, 2),
        "cached_input_tokens": total_cached,
        "input_tokens": total_input,
        "output_tokens": total_output,
        "sample_count": samples,
        "scope": scope,
        "updated_at": newest,
        "host_ip": lan_ip(),
        "lan_url": f"http://{lan_ip()}:8765/cache?scope={scope}",
    }


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path not in ("/cache", "/"):
            log_line(f"HTTP 404 from {self.client_address[0]} path={self.path}")
            self.send_error(404)
            return
        scope = parse_qs(parsed.query).get("scope", ["today"])[0]
        started = time.time()
        try:
            result = collect_usage(scope)
            body = json.dumps(result, ensure_ascii=False).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)
            elapsed = (time.time() - started) * 1000
            log_line(
                f"HTTP 200 from {self.client_address[0]} scope={scope} "
                f"host_ip={result.get('host_ip')} lan_url={result.get('lan_url')} "
                f"requests={result.get('sample_count')} elapsed_ms={elapsed:.1f}"
            )
        except Exception as error:
            log_line(
                f"HTTP 500 from {self.client_address[0]} scope={scope} "
                f"error={type(error).__name__}: {error}\n{traceback.format_exc()}"
            )
            self.send_error(500)

    def log_message(self, *_):
        return


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    log_line(f"Bridge starting host={args.host} port={args.port} lan_ip={lan_ip()}")
    threading.Thread(target=discovery_loop, args=(args.port + 1,), daemon=True).start()
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"Token cache bridge: http://{args.host}:{args.port}/cache")
    server.serve_forever()


if __name__ == "__main__":
    main()
