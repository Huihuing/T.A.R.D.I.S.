import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Home, LayoutDashboard, Wallet, Newspaper, TrendingUp, LogOut, User, ChevronLeft, ChevronRight, Menu, X, Star, MessageCircle, Trophy } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { logoutSession } from '../auth';

interface SidebarProps {
    isOpen?: boolean;
    toggleSidebar?: () => void;
}

export default function Sidebar({ isOpen = true, toggleSidebar }: SidebarProps) {
    const location = useLocation();
    const username = localStorage.getItem('username') || 'Guest';
    const isGuest = username === 'Guest';
    
    const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

    // 💡 Watchlist 메뉴 추가
    const navItems = [
        { path: '/', label: 'Home', icon: Home },
        { path: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
        { path: '/watchlist', label: 'Watchlist', icon: Star },
        { path: '/board', label: 'Community', icon: MessageCircle },
        { path: '/leaderboard', label: 'Leaderboard', icon: Trophy }, // 💡 랭킹 추가
        { path: '/wallet', label: 'Wallet', icon: Wallet },
        { path: '/news', label: 'News', icon: Newspaper },
        { path: '/stock', label: 'Stock & Fund', icon: TrendingUp },
    ];

    const checkIsActive = (path: string) => location.pathname === path;

    const handleLogout = async () => {
        await logoutSession();
        window.location.href = '/login';
    };



    return (
        <>
            <div className="md:hidden w-full bg-slate-900 border-b border-slate-800 p-4 flex justify-between items-center z-[90] relative shrink-0">
                <div className="flex items-center gap-2 text-sky-400 font-extrabold text-2xl">
                    <TrendingUp className="w-7 h-7" /> T.A.R.D.I.S.
                </div>
                <button onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)} className="text-slate-300 hover:text-white transition-colors p-1">
                    {isMobileMenuOpen ? <X className="w-7 h-7" /> : <Menu className="w-7 h-7" />}
                </button>
            </div>

            <AnimatePresence>
                {isMobileMenuOpen && (
                    <motion.div 
                        initial={{ height: 0, opacity: 0 }} 
                        animate={{ height: 'auto', opacity: 1 }} 
                        exit={{ height: 0, opacity: 0 }} 
                        className="md:hidden absolute top-[72px] left-0 w-full bg-slate-900/95 backdrop-blur-xl border-b border-slate-800 z-[90] overflow-hidden shadow-2xl flex flex-col"
                    >
                        <nav className="flex flex-col px-4 py-4 space-y-2">
                            {navItems.map((item) => (
                                <Link 
                                    key={item.path} 
                                    to={item.path} 
                                    onClick={() => setIsMobileMenuOpen(false)} 
                                    className={`flex items-center gap-3 py-3 px-4 rounded-xl transition-all ${
                                        checkIsActive(item.path) ? 'bg-sky-500/10 text-sky-400' : 'text-slate-400 hover:text-slate-200'
                                    }`}
                                >
                                    <item.icon className="w-6 h-6 shrink-0" />
                                    <span className="font-bold">{item.label}</span>
                                </Link>
                            ))}
                            <div className="mt-4 pt-4 border-t border-slate-700">
                                {isGuest ? (
                                    <button onClick={() => window.location.href = '/login'} className="w-full py-3 bg-sky-600 hover:bg-sky-500 text-white rounded-lg text-sm font-bold shadow-lg">
                                        로그인 / 회원가입
                                    </button>
                                ) : (
                                    <button onClick={handleLogout} className="w-full flex items-center justify-center gap-2 py-3 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-sm font-bold border border-slate-700">
                                        <LogOut className="w-4 h-4" /> 로그아웃
                                    </button>
                                )}
                            </div>
                        </nav>
                    </motion.div>
                )}
            </AnimatePresence>

            <motion.div 
                initial={false} 
                animate={{ width: isOpen ? 256 : 80 }} 
                className="hidden md:flex bg-slate-900 border-r border-slate-800 flex-col h-screen sticky top-0 shrink-0 relative z-[90] transition-all"
            >
                {toggleSidebar && (
                    <button 
                        onClick={toggleSidebar} 
                        className="absolute -right-3 top-20 bg-slate-800 border border-slate-700 rounded-full p-1.5 text-slate-400 hover:text-sky-400 hover:border-sky-500 transition-colors shadow-lg z-[100]"
                    >
                        {isOpen ? <ChevronLeft className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                    </button>
                )}

                <div className="h-20 flex items-center px-6 gap-3 text-sky-400 font-extrabold text-2xl overflow-hidden border-b border-slate-800/50">
                    <TrendingUp className="w-8 h-8 shrink-0" />
                    <AnimatePresence mode="wait">
                        {isOpen && (
                            <motion.span 
                                initial={{ opacity: 0, width: 0 }} 
                                animate={{ opacity: 1, width: "auto" }} 
                                exit={{ opacity: 0, width: 0 }} 
                                className="whitespace-nowrap"
                            >
                                T.A.R.D.I.S.
                            </motion.span>
                        )}
                    </AnimatePresence>
                </div>

                <nav className="flex-1 mt-6 px-3 space-y-2 overflow-hidden">
                    {navItems.map((item) => (
                        <Link 
                            key={item.path} 
                            to={item.path} 
                            className={`flex items-center gap-3 py-3 rounded-xl transition-all overflow-hidden ${
                                checkIsActive(item.path) ? 'bg-sky-500/10 text-sky-400' : 'text-slate-400 hover:bg-slate-800 hover:text-slate-200'
                            } ${isOpen ? 'px-4' : 'justify-center px-0'}`}
                        >
                            <item.icon className="w-6 h-6 shrink-0" />
                            <AnimatePresence mode="wait">
                                {isOpen && (
                                    <motion.span 
                                        initial={{ opacity: 0, width: 0 }} 
                                        animate={{ opacity: 1, width: "auto" }} 
                                        exit={{ opacity: 0, width: 0 }} 
                                        className="font-bold whitespace-nowrap"
                                    >
                                        {item.label}
                                    </motion.span>
                                )}
                            </AnimatePresence>
                        </Link>
                    ))}
                </nav>

                <div className={`m-3 bg-slate-800 rounded-xl border border-slate-700 transition-all overflow-hidden ${isOpen ? 'p-4' : 'p-2 flex flex-col items-center'}`}>
                    {isOpen ? (
                        <>
                            <div className="flex items-center gap-3 mb-4">
                                <div className="w-10 h-10 shrink-0 rounded-full bg-slate-700 flex items-center justify-center text-sky-400">
                                    <User className="w-6 h-6" />
                                </div>
                                <div className="overflow-hidden">
                                    <div className="font-bold text-slate-200 truncate">{username}</div>
                                    <div className="text-xs text-green-400 flex items-center gap-1">
                                        <span className="w-2 h-2 rounded-full bg-green-400 inline-block shrink-0"></span> Online
                                    </div>
                                </div>
                            </div>
                            <div className="flex flex-col gap-2">
                                {isGuest ? (
                                    <>
                                        <button onClick={() => window.location.href = '/login'} className="w-full py-2 bg-sky-600 hover:bg-sky-500 text-white rounded-lg text-sm font-bold shadow-lg">로그인</button>
                                        <button onClick={() => window.location.href = '/register'} className="w-full py-2 bg-slate-700 hover:bg-slate-600 text-slate-300 rounded-lg text-sm font-bold border border-slate-600">회원가입</button>
                                    </>
                                ) : (
                                    <button onClick={handleLogout} className="flex items-center justify-center gap-2 w-full py-2 bg-slate-700/50 hover:bg-slate-700 text-slate-300 hover:text-white rounded-lg text-sm font-bold">
                                        <LogOut className="w-4 h-4" /> <span>Logout</span>
                                    </button>
                                )}
                            </div>
                        </>
                    ) : (
                        <button onClick={() => { if (isGuest) window.location.href = '/login'; else handleLogout(); }} className="w-10 h-10 flex items-center justify-center bg-slate-700 hover:bg-sky-600 rounded-full text-slate-300 hover:text-white transition-colors">
                            <LogOut className="w-5 h-5 ml-1" />
                        </button>
                    )}
                </div>
            </motion.div>
        </>
    );
}