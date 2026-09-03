import { useState } from 'react';
import { motion } from 'framer-motion';

export default function Register() {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [accountPassword, setAccountPassword] = useState('');
    const [isLoading, setIsLoading] = useState(false);

    const handleRegister = async (e: React.FormEvent) => {
        e.preventDefault();
        if (accountPassword.length !== 4) return alert("계좌 비밀번호는 숫자 4자리여야 합니다.");

        setIsLoading(true);
        try {
            const res = await fetch('http://localhost:8080/api/member/signup', {
                method: 'POST', 
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password, name, email, accountPassword })
            });
            const data = await res.json();
            
            if (res.ok && data.status === "SUCCESS") {
                alert("회원가입 완료! 1만 달러가 지급된 계좌가 자동 발급되었습니다.");
                window.location.href = '/login';
            } else { 
                alert(`회원가입 실패: ${data.message}`); 
            }
        } catch (error) { 
            alert("서버와 통신할 수 없습니다."); 
        } finally { 
            setIsLoading(false); 
        }
    };

    return (
        <div className="min-h-screen bg-[#0b1120] flex items-center justify-center p-4 font-sans text-white relative">
            <div className="absolute top-10 left-10 w-72 h-72 bg-indigo-500/10 rounded-full blur-3xl pointer-events-none"></div>

            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 p-8 rounded-3xl shadow-2xl w-full max-w-md my-8 z-10">
                <div className="text-center mb-8">
                    <h1 className="text-3xl font-black text-transparent bg-clip-text bg-gradient-to-r from-sky-400 to-indigo-400 mb-2">회원가입</h1>
                    <p className="text-slate-400 text-xs">🚀 개발 편의를 위해 이메일 인증이 생략되었습니다.</p>
                </div>
                
                <form onSubmit={handleRegister} className="flex flex-col gap-4">
                    <div>
                        <label className="block text-slate-400 text-xs font-bold mb-1">이메일 (Email)</label>
                        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required className="w-full bg-slate-900/80 px-4 py-2.5 rounded-xl border border-slate-700 focus:border-sky-500 outline-none text-sm transition-colors" />
                    </div>
                    <div>
                        <label className="block text-slate-400 text-xs font-bold mb-1">아이디 (Username)</label>
                        <input type="text" value={username} onChange={(e) => setUsername(e.target.value)} required className="w-full bg-slate-900/80 px-4 py-2.5 rounded-xl border border-slate-700 focus:border-sky-500 outline-none text-sm transition-colors" />
                    </div>
                    <div>
                        <label className="block text-slate-400 text-xs font-bold mb-1">이름 (Name)</label>
                        <input type="text" value={name} onChange={(e) => setName(e.target.value)} required className="w-full bg-slate-900/80 px-4 py-2.5 rounded-xl border border-slate-700 focus:border-sky-500 outline-none text-sm transition-colors" />
                    </div>
                    <div>
                        <label className="block text-slate-400 text-xs font-bold mb-1">비밀번호 (Password)</label>
                        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required className="w-full bg-slate-900/80 px-4 py-2.5 rounded-xl border border-slate-700 focus:border-sky-500 outline-none text-sm transition-colors" />
                    </div>
                    
                    <div className="bg-rose-950/20 p-4 rounded-xl border border-rose-900/50 mt-2">
                        <label className="block text-rose-400 text-xs font-bold mb-2 flex items-center gap-1"><span>🔒</span> 금융 거래용 계좌 비밀번호 (숫자 4자리)</label>
                        <input type="password" value={accountPassword} maxLength={4} onChange={(e) => setAccountPassword(e.target.value)} required placeholder="****" className="w-full bg-slate-900/80 px-4 py-2.5 rounded-lg border border-slate-700 focus:border-rose-500 outline-none text-center tracking-[1em] font-mono transition-colors" />
                    </div>

                    <button type="submit" disabled={isLoading} className="w-full bg-sky-600 hover:bg-sky-500 text-white font-bold py-3.5 rounded-xl mt-4 transition-colors shadow-lg">
                        {isLoading ? '처리 중...' : '가입 완료 및 계좌 발급'}
                    </button>
                    
                    <button type="button" onClick={() => window.location.href = '/login'} className="text-slate-400 text-sm hover:text-sky-400 transition-colors mt-1">
                        이미 계정이 있으신가요? <span className="font-bold underline">로그인</span>
                    </button>
                </form>
            </motion.div>
        </div>
    );
}