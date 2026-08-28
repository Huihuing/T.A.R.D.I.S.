import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import Dashboard from './pages/Dashboard';
import Login from './pages/Login';
import Wallet from './pages/Wallet';
import Register from './pages/Register'; // 💡 추가
import StockPage from './pages/StockPage';
import NewsPage from './pages/NewsPage';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* 📜 로그인은 이제 /login 주소로 들어가야만 나옵니다 */}
        <Route path="/login" element={<Login />} />

        {/* 📜 기본 화면에는 무조건 사이드바와 대시보드가 나오게 설정합니다 */}
        <Route
          path="/*"
          element={
            <div className="flex min-h-screen bg-slate-950">
              <Sidebar />
              <main className="flex-1 overflow-y-auto">
                <Routes>
                  {/* 주소창에 아무것도 안 쳐도(/) 대시보드가 나옵니다! */}
                  <Route path="/" element={<Dashboard />} />
                  <Route path="/dashboard" element={<Dashboard />} />
                  <Route path="/register" element={<Register />} /> {/* 💡 추가 */}
                  <Route path="/stock" element={<StockPage />} />
                  <Route path="/news" element={<NewsPage />} />
                  <Route path="/login" element={<Login />} />
                  <Route path="/wallet" element={<Wallet />} />
                </Routes>
              </main>
            </div>
          }
        />
      </Routes>
    </BrowserRouter>
  );
}