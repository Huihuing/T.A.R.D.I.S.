import { useState } from 'react';
import { motion } from 'framer-motion';

export default function Login() {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [isLoading, setIsLoading] = useState(false);

    const handleLogin = async (e: React.FormEvent) => {
        e.preventDefault();
        setIsLoading(true);
        try {
            const res = await fetch('http://localhost:8080/api/member/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });
            const data = await res.json();
            if (res.ok && data.status === "SUCCESS") {
                localStorage.setItem('token', data.token);
                localStorage.setItem('username', data.username);
                alert("로그인 성공!");
                window.location.href = '/dashboard';
            } else { alert(`로그인 실패: ${data.message}`); }
        } catch (error) { alert("서버와 통신할 수 없습니다."); } 
        finally { setIsLoading(false); }
    };

    return (
        <div className="min-h-screen bg-slate-900 flex items-center justify-center p-4 font-sans text-white">
            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="bg-slate-800 border border-slate-700 p-8 rounded-2xl shadow-2xl w-full max-w-md">
                <div className="text-center mb-8">
                    <h1 className="text-4xl font-extrabold text-sky-400 mb-2">T.A.R.D.I.S.</h1>
                    <p className="text-slate-400">보안 로그인 (JWT Authentication)</p>
                </div>
                <form onSubmit={handleLogin} className="flex flex-col gap-5">
                    <div>
                        <label className="block text-slate-400 text-sm font-bold mb-2">아이디 (Username)</label>
                        <input type="text" value={username} onChange={(e) => setUsername(e.target.value)} required className="w-full bg-slate-900 px-4 py-3 rounded-lg border border-slate-600 focus:border-sky-500 outline-none" />
                    </div>
                    <div>
                        <label className="block text-slate-400 text-sm font-bold mb-2">비밀번호 (Password)</label>
                        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required className="w-full bg-slate-900 px-4 py-3 rounded-lg border border-slate-600 focus:border-sky-500 outline-none" />
                    </div>
                    <button type="submit" disabled={isLoading} className="w-full bg-sky-600 hover:bg-sky-500 text-white font-bold py-3 rounded-lg mt-4 transition-all shadow-lg">
                        {isLoading ? '인증 중...' : '로그인 (Login)'}
                    </button>
                    {/* 💡 추가된 부분 */}
                    <button type="button" onClick={() => window.location.href = '/register'} className="text-slate-400 text-sm hover:text-sky-400 transition-colors mt-2">
                        계정이 없으신가요? <span className="font-bold underline">회원가입 하기</span>
                    </button>
                </form>
            </motion.div>
        </div>
    );
}