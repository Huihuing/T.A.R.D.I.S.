import { lazy, Suspense, useEffect, useState } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import { getStoredToken, refreshAccessToken } from './auth';
import UiFeedbackHost from './components/UiFeedbackHost';

const Home = lazy(() => import('./pages/Home'));
const Dashboard = lazy(() => import('./pages/Dashboard'));
const Login = lazy(() => import('./pages/Login'));
const Wallet = lazy(() => import('./pages/Wallet'));
const Register = lazy(() => import('./pages/Register'));
const SetupPin = lazy(() => import('./pages/SetupPin'));
const StockPage = lazy(() => import('./pages/StockPage'));
const NewsPage = lazy(() => import('./pages/NewsPage'));
const Watchlist = lazy(() => import('./pages/Watchlist'));
const Board = lazy(() => import('./pages/Board'));
const Leaderboard = lazy(() => import('./pages/Leaderboard'));
const Profile = lazy(() => import('./pages/Profile'));
const ForgotPassword = lazy(() => import('./pages/ForgotPassword'));
const Admin = lazy(() => import('./pages/Admin'));
const Settings = lazy(() => import('./pages/Settings'));
const Trollbox = lazy(() => import('./components/Trollbox'));
const NotificationCenter = lazy(
  () => import('./components/NotificationCenter')
);

function RouteFallback() {
  return (
    <div className="min-h-screen bg-[#0b1120] text-slate-500 flex items-center justify-center">
      화면을 불러오는 중...
    </div>
  );
}


export default function App() {
  const [, setSessionRevision] = useState(0);
  const [isSidebarOpen, setIsSidebarOpen] = useState(() => {
    const saved = localStorage.getItem('sidebarOpen');
    return saved !== null ? JSON.parse(saved) : true;
  });

  useEffect(() => {
    let active = true;

    const syncSessionUi = () => {
      if (active) {
        setSessionRevision(prev => prev + 1);
      }
    };

    const restoreIfNeeded = async () => {
      if (getStoredToken()) return;
      await refreshAccessToken();
      // A failed refresh can still be meaningful: an explicit 401 clears
      // stale username/auth state. Re-render consumers such as Board either
      // way so guest/member UI matches the resulting auth state.
      syncSessionUi();
    };

    restoreIfNeeded();

    const refreshForSignedInUser = async () => {
      const username = localStorage.getItem('username');
      if (!username || username === 'Guest') return;

      await refreshAccessToken();
      syncSessionUi();
    };

    const interval = window.setInterval(
      refreshForSignedInUser,
      10 * 60 * 1000
    );

    const refreshOnFocus = () => {
      refreshForSignedInUser();
    };

    window.addEventListener('focus', refreshOnFocus);

    return () => {
      active = false;
      window.clearInterval(interval);
      window.removeEventListener('focus', refreshOnFocus);
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
      <UiFeedbackHost />
      <Suspense fallback={<RouteFallback />}>
        <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/forgot-password" element={<ForgotPassword />} />
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
                  <Route
                    path="/profile/:username"
                    element={<Profile />}
                  />
                  <Route path="/settings" element={<Settings />} />
                  <Route path="/admin" element={<Admin />} />
                </Routes>
                <NotificationCenter />
                <Trollbox />
              </main>
            </div>
          }
        />
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}
