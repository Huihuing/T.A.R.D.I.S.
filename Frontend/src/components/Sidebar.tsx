import { Link, useLocation } from 'react-router-dom';
// 💡 에러 해결: lucide-react에서 필요한 아이콘들을 전부 import 해옵니다.
import { Home, LayoutDashboard, Wallet, Newspaper, TrendingUp, LogOut, User } from 'lucide-react';

export default function Sidebar() {
    const location = useLocation();
    
    // 로컬 스토리지에서 유저 정보를 가져옵니다. 없으면 'Guest'로 처리.
    const username = localStorage.getItem('username') || 'Guest';
    const isGuest = username === 'Guest';

    // 사이드바 메뉴 리스트
    const navItems = [
        { path: '/', label: 'Home', icon: Home },
        { path: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
        { path: '/wallet', label: 'Wallet', icon: Wallet },
        { path: '/news', label: 'News', icon: Newspaper },
        { path: '/stock', label: 'Stock & Fund', icon: TrendingUp },
    ];

    return (
        <div className="w-64 bg-slate-900 border-r border-slate-800 flex flex-col h-screen sticky top-0">
            {/* 🚀 상단 로고 영역 */}
            <div className="p-6 flex items-center gap-3 text-sky-400 font-extrabold text-2xl">
                <TrendingUp className="w-8 h-8" />
                <span>T.A.R.D.I.S.</span>
            </div>

            {/* 🚀 네비게이션 메뉴 영역 */}
            <nav className="flex-1 px-4 mt-6 space-y-2">
                {navItems.map((item) => {
                    const Icon = item.icon;
                    // 현재 경로가 메뉴 경로와 일치하는지 확인 (활성화 효과)
                    const isActive = location.pathname === item.path || (item.path === '/dashboard' && location.pathname === '/');
                    
                    return (
                        <Link 
                            key={item.path} 
                            to={item.path}
                            className={`flex items-center gap-3 px-4 py-3 rounded-xl transition-all ${
                                isActive 
                                ? 'bg-sky-500/10 text-sky-400' 
                                : 'text-slate-400 hover:bg-slate-800 hover:text-slate-200'
                            }`}
                        >
                            <Icon className="w-5 h-5" />
                            <span className="font-bold">{item.label}</span>
                        </Link>
                    );
                })}
            </nav>

            {/* 🚀 하단 유저 프로필 및 로그인/로그아웃 버튼 영역 */}
            <div className="p-4 m-4 bg-slate-800 rounded-xl border border-slate-700">
                <div className="flex items-center gap-3 mb-4">
                    <div className="w-10 h-10 rounded-full bg-slate-700 flex items-center justify-center text-sky-400">
                        <User className="w-6 h-6" />
                    </div>
                    <div>
                        <div className="font-bold text-slate-200 truncate max-w-[120px]">{username}</div>
                        <div className="text-xs text-green-400 flex items-center gap-1">
                            <span className="w-2 h-2 rounded-full bg-green-400 inline-block"></span> Online
                        </div>
                    </div>
                </div>

                {/* 하단 유저 프로필 및 인증 버튼 영역 */}
                <div className="flex flex-col gap-2">
                    {isGuest ? (
                        <>
                            <button onClick={() => window.location.href = '/login'} className="flex items-center justify-center gap-2 w-full py-2 bg-sky-600 hover:bg-sky-500 text-white rounded-lg transition-colors text-sm font-bold shadow-lg">
                                Login (로그인)
                            </button>
                            <button onClick={() => window.location.href = '/register'} className="flex items-center justify-center gap-2 w-full py-2 bg-slate-700 hover:bg-slate-600 text-slate-300 hover:text-white rounded-lg transition-colors text-sm font-bold border border-slate-600">
                                Register (회원가입)
                            </button>
                        </>
                    ) : (
                        <button onClick={() => {
                            localStorage.removeItem('username');
                            localStorage.removeItem('token'); 
                            alert("로그아웃 되었습니다.");
                            window.location.href = '/login'; 
                        }} className="flex items-center justify-center gap-2 w-full py-2 bg-slate-700/50 hover:bg-slate-700 text-slate-300 hover:text-white rounded-lg transition-colors text-sm font-bold">
                            <LogOut className="w-4 h-4" /> 
                            <span>Logout</span>
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
}