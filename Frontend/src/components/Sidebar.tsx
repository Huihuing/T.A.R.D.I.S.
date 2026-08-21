import { Link, useLocation } from 'react-router-dom';
import { Home, LayoutDashboard, Wallet, Newspaper, TrendingUp, Settings, Phone } from 'lucide-react';

export default function Sidebar() {
    const location = useLocation();

    const menuItems = [
        { name: 'Home', path: '/', icon: <Home size={20} /> },
        { name: 'Dashboard', path: '/dashboard', icon: <LayoutDashboard size={20} /> },
        { name: 'Wallet', path: '/wallet', icon: <Wallet size={20} /> },
        { name: 'News', path: '/news', icon: <Newspaper size={20} /> },
        { name: 'Stock & Fund', path: '/stock', icon: <TrendingUp size={20} /> },
    ];

    return (
        <div className="w-64 bg-slate-900 border-r border-slate-800 text-slate-300 flex flex-col h-screen sticky top-0">
            <div className="p-6 mb-4">
                <h1 className="text-2xl font-extrabold text-sky-400 flex items-center gap-2">
                    <TrendingUp className="text-sky-400" /> T.A.R.D.I.S.
                </h1>
            </div>

            <nav className="flex-1 px-4 space-y-2">
                {menuItems.map((item) => {
                    const isActive = location.pathname === item.path;
                    return (
                        <Link
                            key={item.name}
                            to={item.path}
                            className={`flex items-center gap-3 px-4 py-3 rounded-xl transition-colors ${isActive ? 'bg-sky-500/10 text-sky-400 font-bold' : 'hover:bg-slate-800 hover:text-white'
                                }`}
                        >
                            {item.icon}
                            {item.name}
                        </Link>
                    );
                })}
            </nav>

            <div className="p-4 border-t border-slate-800 space-y-2">
                <button className="flex items-center gap-3 px-4 py-3 w-full text-left rounded-xl hover:bg-slate-800 transition-colors">
                    <Settings size={20} /> Settings
                </button>
                <button className="flex items-center gap-3 px-4 py-3 w-full text-left rounded-xl hover:bg-slate-800 transition-colors">
                    <Phone size={20} /> Contact us
                </button>
            </div>
        </div>
    );
}