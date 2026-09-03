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
        <div className="min-h-screen bg-[#0b1120] flex items-center justify-center p-4 font-sans text-white relative">
            <div className="absolute top-10 right-10 w-72 h-72 bg-sky-500/10 rounded-full blur-3xl pointer-events-none"></div>
            <div className="absolute bottom-10 left-10 w-96 h-96 bg-indigo-500/10 rounded-full blur-3xl pointer-events-none"></div>

            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 p-8 md:p-10 rounded-3xl shadow-2xl w-full max-w-md z-10">
                <div className="text-center mb-10">
                    <h1 className="text-4xl font-black text-transparent bg-clip-text bg-gradient-to-r from-sky-400 to-indigo-400 mb-2">T.A.R.D.I.S.</h1>
                    <p className="text-slate-400 text-sm">Welcome back to the market.</p>
                </div>
                <form onSubmit={handleLogin} className="flex flex-col gap-5">
                    <div>
                        <label className="block text-slate-400 text-sm font-bold mb-2">아이디 (Username)</label>
                        <input type="text" value={username} onChange={(e) => setUsername(e.target.value)} required className="w-full bg-slate-900/80 px-4 py-3 rounded-xl border border-slate-700 focus:border-sky-500 outline-none transition-colors" />
                    </div>
                    <div>
                        <label className="block text-slate-400 text-sm font-bold mb-2">비밀번호 (Password)</label>
                        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required className="w-full bg-slate-900/80 px-4 py-3 rounded-xl border border-slate-700 focus:border-sky-500 outline-none transition-colors" />
                    </div>
                    <button type="submit" disabled={isLoading} className="w-full bg-sky-600 hover:bg-sky-500 text-white font-bold py-3.5 rounded-xl mt-4 transition-colors shadow-lg">
                        {isLoading ? '인증 중...' : '로그인 (Login)'}
                    </button>
                    <button type="button" onClick={() => window.location.href = '/register'} className="text-slate-400 text-sm hover:text-sky-400 transition-colors mt-2">
                        계정이 없으신가요? <span className="font-bold underline">회원가입 하기</span>
                    </button>
                </form>
            </motion.div>
        </div>
    );
}