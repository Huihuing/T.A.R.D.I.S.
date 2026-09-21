import { API_URL, WS_URL } from '../config';
import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Home } from 'lucide-react'; // 💡 아이콘 추가

export default function Login() {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const navigate = useNavigate();

    const handleLogin = async (e: React.FormEvent) => {
        e.preventDefault();
        setIsLoading(true);
        try {
            const res = await fetch(`${API_URL}/api/auth/login`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });
            const data = await res.json();
            if (res.ok && data.token) {
                localStorage.setItem('token', data.token);
                localStorage.setItem('username', data.username || username.trim());
                
                // 💡 일일 출석 보상 알림
                if (data.dailyReward) {
                    alert('🎉 일일 출석 체크 완료!\n시드머니 $500이 추가로 지급되었습니다.');
                }
                
                navigate('/dashboard');
            } else {
                alert(data.message || '로그인 실패');
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
                <h1 className="text-3xl font-black text-white text-center mb-6">로그인</h1>
                <form onSubmit={handleLogin} className="flex flex-col gap-4">
                    <div>
                        <label className="block text-sm text-slate-400 mb-1 font-bold">아이디 (Username)</label>
                        <input type="text" value={username} onChange={e => setUsername(e.target.value)} required className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white outline-none focus:border-sky-500" />
                    </div>
                    <div>
                        <label className="block text-sm text-slate-400 mb-1 font-bold">비밀번호 (Password)</label>
                        <input type="password" value={password} onChange={e => setPassword(e.target.value)} required className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white outline-none focus:border-sky-500" />
                    </div>
                    <button type="submit" disabled={isLoading} className="w-full bg-sky-600 hover:bg-sky-500 text-white font-bold py-3.5 rounded-xl mt-4 transition-colors shadow-lg disabled:opacity-50">
                        {isLoading ? '로그인 중...' : '로그인'}
                    </button>
                </form>
                <div className="text-center mt-6 text-sm text-slate-400">
                    계정이 없으신가요? <Link to="/register" className="text-sky-400 font-bold hover:underline">회원가입</Link>
                </div>
            </div>
        </div>
    );
}