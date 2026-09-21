import { useEffect, useState } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import Home from './pages/Home';
import Dashboard from './pages/Dashboard';
import Login from './pages/Login';
import Wallet from './pages/Wallet';
import Register from './pages/Register';
import SetupPin from './pages/SetupPin';
import StockPage from './pages/StockPage';
import NewsPage from './pages/NewsPage';
import Watchlist from './pages/Watchlist';
import Board from './pages/Board';
import Trollbox from './components/Trollbox';
import NotificationCenter from './components/NotificationCenter';
import Leaderboard from './pages/Leaderboard';
import { getStoredToken, refreshAccessToken } from './auth';

export default function App() {
  const [, setSessionRevision] = useState(0);
  const [isSidebarOpen, setIsSidebarOpen] = useState(() => {
    const saved = localStorage.getItem('sidebarOpen');
    return saved !== null ? JSON.parse(saved) : true;
  });

  useEffect(() => {
    let active = true;

    const restoreIfNeeded = async () => {
      if (getStoredToken()) return;
      const restored = await refreshAccessToken();
      if (active && restored) {
        setSessionRevision(prev => prev + 1);
      }
    };

    restoreIfNeeded();

    const interval = window.setInterval(async () => {
      const username = localStorage.getItem('username');
      if (!username || username === 'Guest') return;

      const refreshed = await refreshAccessToken();
      if (active && refreshed) {
        setSessionRevision(prev => prev + 1);
      }
    }, 10 * 60 * 1000);

    return () => {
      active = false;
      window.clearInterval(interval);
    };
  }, []);

  const toggleSidebar = () => {
    setIsSidebarOpen((prev: boolean) => {
      const newState = !prev;
      localStorage.setItem('sidebarOpen', JSON.stringify(newState));
      return newState;
    });
  };

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/setup-pin" element={<SetupPin />} />

        <Route
          path="/*"
          element={
            <div className="flex flex-col md:flex-row min-h-screen bg-[#0b1120] overflow-hidden font-sans">
              <Sidebar
                isOpen={isSidebarOpen}
                toggleSidebar={toggleSidebar}
              />

              <main className="flex-1 h-screen overflow-y-auto relative transition-all">
                <Routes>
                  <Route path="/" element={<Home />} />
                  <Route path="/dashboard" element={<Dashboard />} />
                  <Route path="/stock" element={<StockPage />} />
                  <Route path="/news" element={<NewsPage />} />
                  <Route path="/wallet" element={<Wallet />} />
                  <Route path="/watchlist" element={<Watchlist />} />
                  <Route path="/board" element={<Board />} />
                  <Route
                    path="/leaderboard"
                    element={<Leaderboard />}
                  />
                </Routes>
                <NotificationCenter />
                <Trollbox />
              </main>
            </div>
          }
        />
      </Routes>
    </BrowserRouter>
  );
}
