import tkinter as tk
import os

# 💡 이 경로들이 실제 컴퓨터의 폴더 위치와 똑같은지 꼭 확인해 주세요!
BACKEND_DIR = r"C:\TARDIS\Backend"
FRONTEND_DIR = r"C:\TARDIS\Frontend"
# 👇 server.js 파일이 들어있는 진짜 폴더 경로를 여기에 적어야 합니다.
SOCKET_DIR = r"C:\TARDIS\Socket_Server" 

def start_backend():
    os.system(f'start "T.A.R.D.I.S Backend" cmd /k "cd /d {BACKEND_DIR} && gradlew bootRun"')

def start_frontend():
    os.system(f'start "T.A.R.D.I.S Frontend" cmd /k "cd /d {FRONTEND_DIR} && npm run dev"')

def start_socket():
    # 💡 요청하신 대로 실행 명령어를 node server.js로 변경했습니다.
    os.system(f'start "T.A.R.D.I.S Socket_Server" cmd /k "cd /d {SOCKET_DIR} && node server.js"')

# GUI 창 설정
root = tk.Tk()
root.title("T.A.R.D.I.S. Manager")
root.geometry("300x250")
root.configure(bg="#0f172a")

# UI 타이틀
tk.Label(root, text="Server Controller", font=("Helvetica", 16, "bold"), bg="#0f172a", fg="#38bdf8").pack(pady=15)

# 실행 버튼들
tk.Button(root, text="▶ Start Backend", command=start_backend, width=20, bg="#334155", fg="white", font=("Helvetica", 10, "bold"), relief="flat").pack(pady=5)
tk.Button(root, text="▶ Start Frontend", command=start_frontend, width=20, bg="#334155", fg="white", font=("Helvetica", 10, "bold"), relief="flat").pack(pady=5)
tk.Button(root, text="▶ Start Socket", command=start_socket, width=20, bg="#334155", fg="white", font=("Helvetica", 10, "bold"), relief="flat").pack(pady=5)

root.mainloop()