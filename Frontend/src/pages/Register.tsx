import { API_URL, WS_URL } from '../config';
import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Home } from 'lucide-react'; // 💡 아이콘 추가

export default function Register() {
    const [email, setEmail] = useState('');
    const [username, setUsername] = useState('');
    const [name, setName] = useState('');
    const [password, setPassword] = useState('');
    const [pin, setPin] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const navigate = useNavigate();

    const handleRegister = async (e: React.FormEvent) => {
        e.preventDefault();
        setIsLoading(true);
        try {
            const res = await fetch(`${API_URL}/api/auth/register`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, username, name, password, pin })
            });
            const data = await res.json();
            if (res.ok) {
                alert('회원가입 성공! 로그인해 주세요.');
                navigate('/login');
            } else {
                alert(`회원가입 실패: ${data.message || '오류가 발생했습니다.'}`);
            }
        } catch (err) {
            alert('서버 오류가 발생했습니다.');
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className="min-h-screen bg-[#0b1120] text-slate-200 flex items-center justify-center p-4 relative">
            {/* 💡 홈으로 돌아가기 버튼 */}
            <Link to="/" className="absolute top-6 left-6 flex items-center gap-2 text-slate-400 hover:text-sky-400 font-bold transition-colors bg-slate-800/50 px-4 py-2 rounded-xl border border-slate-700/50">
                <Home className="w-5 h-5" /> 홈으로
            </Link>

            <div className="bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 p-8 rounded-3xl w-full max-w-md shadow-2xl">
                <h1 className="text-3xl font-black text-white text-center mb-6">회원가입</h1>
                <form onSubmit={handleRegister} className="flex flex-col gap-4">
                    <div>
                        <label className="block text-sm text-slate-400 mb-1 font-bold">이메일 (Email)</label>
                        <input type="email" value={email} onChange={e => setEmail(e.target.value)} required className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white outline-none focus:border-sky-500" />
                    </div>
                    <div>
                        <label className="block text-sm text-slate-400 mb-1 font-bold">아이디 (Username)</label>
                        <input type="text" value={username} onChange={e => setUsername(e.target.value)} required className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white outline-none focus:border-sky-500" />
                    </div>
                    <div>
                        <label className="block text-sm text-slate-400 mb-1 font-bold">이름 (Name)</label>
                        <input type="text" value={name} onChange={e => setName(e.target.value)} required className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white outline-none focus:border-sky-500" />
                    </div>
                    <div>
                        <label className="block text-sm text-slate-400 mb-1 font-bold">비밀번호 (Password)</label>
                        <input type="password" value={password} onChange={e => setPassword(e.target.value)} required className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white outline-none focus:border-sky-500" />
                    </div>
                    <div>
                        <label className="block text-sm text-slate-400 mb-1 font-bold">계좌 비밀번호 (PIN 4자리)</label>
                        <input type="password" maxLength={4} value={pin} onChange={e => setPin(e.target.value)} required className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white outline-none focus:border-sky-500 font-mono tracking-widest" />
                    </div>
                    <button type="submit" disabled={isLoading} className="w-full bg-sky-600 hover:bg-sky-500 text-white font-bold py-3.5 rounded-xl mt-4 transition-colors shadow-lg disabled:opacity-50">
                        {isLoading ? '처리 중...' : '회원가입'}
                    </button>
                </form>
                <div className="text-center mt-6 text-sm text-slate-400">
                    이미 계정이 있으신가요? <Link to="/login" className="text-sky-400 font-bold hover:underline">로그인</Link>
                </div>
            </div>
        </div>
    );
}