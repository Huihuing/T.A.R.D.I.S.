import os
import sys
import socket
import subprocess
import tkinter as tk
from tkinter import font

# 기본 디렉터리 경로 설정 (launcher.py 위치 기준 자동 감지)
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
BACKEND_DIR = os.path.join(BASE_DIR, "Backend")
FRONTEND_DIR = os.path.join(BASE_DIR, "Frontend")
SOCKET_DIR = os.path.join(BASE_DIR, "Socket_Server")

# 서비스 설정
SERVICES = {
    "backend": {
        "name": "Backend (Spring Boot)",
        "port": 8080,
        "dir": BACKEND_DIR,
        "cmd": f'cmd.exe /k "title TARDIS Backend && cd /d "{BACKEND_DIR}" && set "JAVA_HOME=C:\\Program Files\\Java\\jdk-21" && call .\\gradlew.bat bootRun"',
        "proc": None,
    },
    "frontend": {
        "name": "Frontend (React + Vite)",
        "port": 5173,
        "dir": FRONTEND_DIR,
        "cmd": f'cmd.exe /k "title TARDIS Frontend && cd /d "{FRONTEND_DIR}" && npm run dev"',
        "proc": None,
    },
    "socket": {
        "name": "Socket Server (Node.js)",
        "port": 3000,
        "dir": SOCKET_DIR,
        "cmd": f'cmd.exe /k "title TARDIS Socket_Server && cd /d "{SOCKET_DIR}" && node server.js"',
        "proc": None,
    },
}

def is_port_in_use(port):
    """지정된 포트가 현재 열려 있는지 확인"""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(0.2)
            return s.connect_ex(('127.0.0.1', port)) == 0
    except Exception:
        return False

def kill_port(port):
    """해당 포트를 점유하고 있는 프로세스를 강제 종료"""
    try:
        res = subprocess.run('netstat -ano -p tcp', capture_output=True, text=True, shell=True)
        pids = set()
        for line in res.stdout.splitlines():
            line = line.strip()
            if f':{port}' in line and 'LISTENING' in line:
                parts = line.split()
                if len(parts) >= 5:
                    pids.add(parts[-1])
        for pid in pids:
            if pid and pid != '0':
                subprocess.run(f'taskkill /F /PID {pid}', shell=True, capture_output=True)
    except Exception:
        pass

def start_service(key):
    """개별 서비스 시작"""
    srv = SERVICES[key]
    if (srv["proc"] and srv["proc"].poll() is None) or is_port_in_use(srv["port"]):
        return
    srv["proc"] = subprocess.Popen(srv["cmd"], creationflags=subprocess.CREATE_NEW_CONSOLE)
    update_ui()

def stop_service(key):
    """개별 서비스 종료 (프로세스 트리 및 포트 점유 프로세스 모두 정리)"""
    srv = SERVICES[key]
    if srv["proc"] and srv["proc"].poll() is None:
        try:
            subprocess.run(f'taskkill /F /T /PID {srv["proc"].pid}', shell=True, capture_output=True)
        except Exception:
            pass
    srv["proc"] = None
    kill_port(srv["port"])
    update_ui()

def start_all():
    """모든 서비스 일괄 시작"""
    for key in SERVICES:
        start_service(key)

def stop_all():
    """모든 서비스 일괄 종료"""
    for key in SERVICES:
        stop_service(key)

# GUI 구성
root = tk.Tk()
root.title("T.A.R.D.I.S. Control Center")
root.geometry("480x430")
root.configure(bg="#0f172a")
root.resizable(False, False)

# 헤더 영역
header_frame = tk.Frame(root, bg="#0f172a")
header_frame.pack(fill="x", padx=20, pady=(15, 10))

title_label = tk.Label(
    header_frame,
    text="T.A.R.D.I.S. Control Center",
    font=("Segoe UI", 16, "bold"),
    bg="#0f172a",
    fg="#38bdf8"
)
title_label.pack(anchor="w")

sub_label = tk.Label(
    header_frame,
    text="통합 서버 관리자 (Backend / Frontend / Socket)",
    font=("Segoe UI", 9),
    bg="#0f172a",
    fg="#94a3b8"
)
sub_label.pack(anchor="w")

# 서비스 목록 프레임
cards_frame = tk.Frame(root, bg="#0f172a")
cards_frame.pack(fill="both", expand=True, padx=20, pady=5)

ui_elements = {}

for key, srv in SERVICES.items():
    card = tk.Frame(cards_frame, bg="#1e293b", bd=0, highlightthickness=1, highlightbackground="#334155")
    card.pack(fill="x", pady=6, ipady=8, padx=2)

    # 좌측: 이름 및 상태
    info_frame = tk.Frame(card, bg="#1e293b")
    info_frame.pack(side="left", padx=12, fill="y")

    name_label = tk.Label(
        info_frame,
        text=srv["name"],
        font=("Segoe UI", 11, "bold"),
        bg="#1e293b",
        fg="#f8fafc"
    )
    name_label.pack(anchor="w")

    status_label = tk.Label(
        info_frame,
        text=f"● 정지됨 (Port {srv['port']})",
        font=("Segoe UI", 9),
        bg="#1e293b",
        fg="#94a3b8"
    )
    status_label.pack(anchor="w")

    # 우측: 시작 / 종료 버튼
    btn_frame = tk.Frame(card, bg="#1e293b")
    btn_frame.pack(side="right", padx=12)

    btn_start = tk.Button(
        btn_frame,
        text="▶ Start",
        command=lambda k=key: start_service(k),
        font=("Segoe UI", 9, "bold"),
        bg="#059669",
        fg="white",
        activebackground="#10b981",
        activeforeground="white",
        width=7,
        relief="flat",
        cursor="hand2"
    )
    btn_start.pack(side="left", padx=3)

    btn_stop = tk.Button(
        btn_frame,
        text="⏹ Stop",
        command=lambda k=key: stop_service(k),
        font=("Segoe UI", 9, "bold"),
        bg="#dc2626",
        fg="white",
        activebackground="#ef4444",
        activeforeground="white",
        width=7,
        relief="flat",
        cursor="hand2"
    )
    btn_stop.pack(side="left", padx=3)

    ui_elements[key] = {
        "status": status_label,
        "btn_start": btn_start,
        "btn_stop": btn_stop,
    }

# 하단 일괄 제어 영역
bottom_frame = tk.Frame(root, bg="#0f172a")
bottom_frame.pack(fill="x", padx=20, pady=(10, 18))

btn_start_all = tk.Button(
    bottom_frame,
    text="🚀 Start All (전체 시작)",
    command=start_all,
    font=("Segoe UI", 10, "bold"),
    bg="#2563eb",
    fg="white",
    activebackground="#3b82f6",
    activeforeground="white",
    height=2,
    relief="flat",
    cursor="hand2"
)
btn_start_all.pack(side="left", fill="x", expand=True, padx=(0, 6))

btn_stop_all = tk.Button(
    bottom_frame,
    text="🛑 Stop All (전체 종료)",
    command=stop_all,
    font=("Segoe UI", 10, "bold"),
    bg="#991b1b",
    fg="white",
    activebackground="#b91c1c",
    activeforeground="white",
    height=2,
    relief="flat",
    cursor="hand2"
)
btn_stop_all.pack(side="right", fill="x", expand=True, padx=(6, 0))

def update_ui():
    """주기적으로 각 서비스의 포트/프로세스 상태를 감지하여 UI 업데이트"""
    for key, srv in SERVICES.items():
        port_open = is_port_in_use(srv["port"])
        proc_alive = srv["proc"] is not None and srv["proc"].poll() is None

        status_lbl = ui_elements[key]["status"]
        if port_open:
            status_lbl.config(
                text=f"● 실행 중 (Port {srv['port']})",
                fg="#34d399"  # 녹색
            )
        elif proc_alive:
            status_lbl.config(
                text=f"● 부팅 중... (Port {srv['port']})",
                fg="#fbbf24"  # 주황색
            )
        else:
            status_lbl.config(
                text=f"● 정지됨 (Port {srv['port']})",
                fg="#94a3b8"  # 회색
            )

    root.after(1000, update_ui)

# 1초마다 상태 갱신 루프 시작
root.after(100, update_ui)

# 프로그램 종료 시 정리
def on_close():
    root.destroy()

root.protocol("WM_DELETE_WINDOW", on_close)

if __name__ == "__main__":
    root.mainloop()