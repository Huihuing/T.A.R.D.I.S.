import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { TrendingUp } from 'lucide-react';

export default function Login() {
    // 화면 모드 (true면 로그인 화면, false면 회원가입 화면)
    const [isLoginMode, setIsLoginMode] = useState(true);

    // 사용자가 입력한 값들을 저장할 공간
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [nickname, setNickname] = useState('');

    // 성공 시 대시보드로 이동시켜줄 네비게이터
    const navigate = useNavigate();

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault(); // 버튼 눌렀을 때 페이지가 새로고침 되는 것을 막음

        // 로그인 모드인지 회원가입 모드인지에 따라 백엔드 목적지 변경
        const endpoint = isLoginMode ? '/api/auth/login' : '/api/auth/signup';

        try {
            const res = await fetch(`http://localhost:8080${endpoint}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password, nickname }),
            });

            const data = await res.json();

            if (data.status === 'SUCCESS') {
                alert(data.message);
                if (isLoginMode) {
                    // 로그인 성공 -> 대시보드로 이동!
                    navigate('/dashboard');
                } else {
                    // 회원가입 성공 -> 입력창 비우고 로그인 화면으로 전환
                    setPassword('');
                    setIsLoginMode(true);
                }
            } else {
                // 실패 (비밀번호 틀림, 아이디 중복 등)
                alert(data.message);
            }
        } catch (err) {
            alert("서버와 연결할 수 없습니다. 백엔드가 켜져 있는지 확인해주세요.");
        }
    };

    return (
        <div className="min-h-screen bg-slate-950 flex flex-col items-center justify-center p-4">
            {/* 로고 부분 */}
            <div className="mb-8 text-center">
                <h1 className="text-4xl font-extrabold text-sky-400 flex items-center justify-center gap-2 mb-2">
                    <TrendingUp className="text-sky-400" size={36} /> T.A.R.D.I.S.
                </h1>
                <p className="text-slate-400">Time And Relative Dimension In Stocks</p>
            </div>

            <div className="bg-slate-900 p-8 rounded-2xl shadow-2xl w-full max-w-md border border-slate-800">
                <h2 className="text-2xl font-bold text-white mb-6 text-center">
                    {isLoginMode ? 'Sign In to Your Account' : 'Create New Account'}
                </h2>

                <form onSubmit={handleSubmit} className="space-y-5">
                    {/* 회원가입일 때만 닉네임 입력창 표시 */}
                    {!isLoginMode && (
                        <div>
                            <label className="block text-slate-400 mb-2 text-sm font-bold">Nickname</label>
                            <input
                                type="text"
                                required
                                value={nickname}
                                onChange={(e) => setNickname(e.target.value)}
                                className="w-full bg-slate-950 border border-slate-700 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-sky-500 transition-colors"
                                placeholder="Enter your nickname"
                            />
                        </div>
                    )}

                    <div>
                        <label className="block text-slate-400 mb-2 text-sm font-bold">ID (Username)</label>
                        <input
                            type="text"
                            required
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            className="w-full bg-slate-950 border border-slate-700 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-sky-500 transition-colors"
                            placeholder="Enter your ID"
                        />
                    </div>

                    <div>
                        <label className="block text-slate-400 mb-2 text-sm font-bold">Password</label>
                        <input
                            type="password"
                            required
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            className="w-full bg-slate-950 border border-slate-700 rounded-lg px-4 py-3 text-white focus:outline-none focus:border-sky-500 transition-colors"
                            placeholder="••••••••"
                        />
                    </div>

                    <button
                        type="submit"
                        className="w-full bg-sky-500 hover:bg-sky-600 text-white font-bold py-3.5 rounded-lg transition-colors mt-4 shadow-lg shadow-sky-500/20"
                    >
                        {isLoginMode ? 'Login' : 'Sign Up'}
                    </button>
                </form>

                {/* 로그인 <-> 회원가입 전환 버튼 */}
                <div className="mt-6 text-center text-slate-400 text-sm">
                    {isLoginMode ? "Don't have an account? " : "Already have an account? "}
                    <button
                        onClick={() => setIsLoginMode(!isLoginMode)}
                        className="text-sky-400 font-bold hover:underline"
                    >
                        {isLoginMode ? 'Sign up' : 'Sign in'}
                    </button>
                </div>
            </div>
        </div>
    );
}