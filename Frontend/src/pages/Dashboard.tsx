import { API_URL, WS_URL } from '../config';
import { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Search, Briefcase, RefreshCw, Newspaper, Gift } from 'lucide-react';
// 💡 Recharts 라이브러리 임포트
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip as RechartsTooltip, AreaChart, Area, XAxis, YAxis, CartesianGrid } from 'recharts';
import EconomyModal from '../components/EconomyModal';

interface TradeHistory { id: number; tradeType: string; symbol: string; amount: number; price: number; tradeTime: string; }
interface PortfolioItem { symbol: string; amount: number; averagePrice: number; }
interface PortfolioSnapshot { id: number; cashBalance: number; investedValue: number; totalAssets: number; capturedAt: string; }
interface StockSymbol { symbol: string; description: string; displaySymbol: string; }
interface NewsItem { id: number; headline: string; summary: string; url: string; image: string; }
interface NaverNewsItem { title: string; link: string; description: string; pubDate: string; }
interface TradeToast { id: number; type: 'BUY' | 'SELL' | 'TRANSFER'; symbol?: string; amount?: number; price?: number; customMessage?: string; }

const DASHBOARD_SYMBOLS = ['AAPL', 'TSLA', 'MSFT', 'NVDA', 'GOOGL', 'AMZN', 'META', 'AMD', 'COIN', 'NFLX', 'INTC', 'DIS'];
const BATCH_SIZE = 4;

export default function Dashboard() {
    const [balance, setBalance] = useState<number>(0);
    const [history, setHistory] = useState<TradeHistory[]>([]);
    const [portfolio, setPortfolio] = useState<PortfolioItem[]>([]);
    const [tradeAmount, setTradeAmount] = useState<number | ''>(1);
    const [selectedSymbol, setSelectedSymbol] = useState<string>('AAPL');
    const [resolution, setResolution] = useState<string>('D'); 
    const [allSymbols, setAllSymbols] = useState<StockSymbol[]>([]);
    const [searchTerm, setSearchTerm] = useState<string>('');
    const [isDropdownOpen, setIsDropdownOpen] = useState<boolean>(false);
    const [watchlistData, setWatchlistData] = useState<Record<string, { c: number, d: number, dp: number }>>({});
    const [currentPrice, setCurrentPrice] = useState<number>(0);
    const [priceChange, setPriceChange] = useState<number>(0);
    const [percentChange, setPercentChange] = useState<number>(0);
    const [totalAssets, setTotalAssets] = useState<number>(0);
    const [buyRatio, setBuyRatio] = useState<number>(50); 
    const [panelMode, setPanelMode] = useState<'summary' | 'top4'>('summary'); 
    const [isPortfolioModalOpen, setIsPortfolioModalOpen] = useState<boolean>(false);
    const [isEconomyModalOpen, setIsEconomyModalOpen] = useState<boolean>(false);
    const [toasts, setToasts] = useState<TradeToast[]>([]);
    
    const [stocks, setStocks] = useState<any[]>([]);
    const [stockIndex, setStockIndex] = useState(0);
    const [isLoadingStocks, setIsLoadingStocks] = useState(false);
    const [assetHistory, setAssetHistory] = useState<PortfolioSnapshot[]>([]);
    const [assetHistoryRange, setAssetHistoryRange] = useState<'1D' | '1W' | '1M' | 'ALL'>('1W');
    const [isAssetHistoryLoading, setIsAssetHistoryLoading] = useState(false);

    const [newsTab, setNewsTab] = useState<'global' | 'korea'>('global'); 
    const [globalNewsList, setGlobalNewsList] = useState<NewsItem[]>([]);
    const [koreaNewsList, setKoreaNewsList] = useState<NaverNewsItem[]>([]);
    const [newsIndex, setNewsIndex] = useState(0);
    const [isLoadingNews, setIsLoadingNews] = useState(false);

    const getAuthHeaders = () => {
        const token = localStorage.getItem('token');
        return { 'Content-Type': 'application/json', ...(token && { 'Authorization': `Bearer ${token}` }) };
    };

    useEffect(() => {
        const username = localStorage.getItem('username');
        if (!username) return;
        const stompClient = new Client({
            webSocketFactory: () => new SockJS(`${API_URL}/ws-stomp`),
            reconnectDelay: 5000,
            onConnect: () => {
                stompClient.subscribe(`/topic/alerts/${username}`, (message) => {
                    const data = JSON.parse(message.body);
                    const toastId = Date.now();
                    setToasts(prev => [...prev, { id: toastId, type: data.type, customMessage: data.message }]);
                    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== toastId)), 4000);
                    fetchUserData();
                });
            }
        });
        stompClient.activate();
        return () => { stompClient.deactivate(); };
    }, []);

    const showLocalToast = (type: 'BUY' | 'SELL', symbol: string, amount: number, price: number) => {
        const id = Date.now();
        setToasts(prev => [...prev, { id, type, symbol, amount, price }]);
        setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 4000);
    };

    const fetchUserData = async () => {
        const username = localStorage.getItem('username');
        if (!username) return;
        try {
            const headers = getAuthHeaders();
            const balRes = await fetch(`${API_URL}/api/trade/balance`, { headers });
            setBalance(Number(await balRes.text()) || 0);
            
            const histRes = await fetch(`${API_URL}/api/trade/history`, { headers });
            const histData: TradeHistory[] = await histRes.json();
            setHistory(histData);
            if (histData.length > 0) setBuyRatio(Math.round((histData.filter(h => h.tradeType === 'BUY').length / histData.length) * 100));
            
            const portRes = await fetch(`${API_URL}/api/trade/portfolio`, { headers });
            setPortfolio(await portRes.json());
        } catch (err) {}
    };

    const fetchAssetHistory = async (range = assetHistoryRange) => {
        const token = localStorage.getItem('token');
        if (!token) {
            setAssetHistory([]);
            return;
        }

        setIsAssetHistoryLoading(true);
        try {
            const res = await fetch(
                `${API_URL}/api/portfolio-history?range=${range}`,
                { headers: getAuthHeaders() }
            );
            const data = await res.json().catch(() => []);
            if (res.ok && Array.isArray(data)) {
                setAssetHistory(data);
            }
        } catch {
            setAssetHistory([]);
        } finally {
            setIsAssetHistoryLoading(false);
        }
    };

    const fetchStockBatch = async (startIdx: number) => {
        setIsLoadingStocks(true);
        const symbolsToFetch = DASHBOARD_SYMBOLS.slice(startIdx, startIdx + BATCH_SIZE);
        try {
            const promises = symbolsToFetch.map(async (symbol) => {
                const res = await fetch(`${API_URL}/api/stock/quote?symbol=${symbol}`);
                if (res.status === 429) return { symbol, error: '한도 대기' };
                const data = await res.json();
                return { symbol, ...data };
            });
            setStocks(await Promise.all(promises));
        } catch (error) {} finally { setIsLoadingStocks(false); }
    };

    const fetchNewsData = async () => {
        setIsLoadingNews(true);
        try {
            const resGlobal = await fetch(`${API_URL}/api/news/global?symbol=AAPL`);
            const globalData = await resGlobal.json();
            if (Array.isArray(globalData)) setGlobalNewsList(globalData);
            
            const resKorea = await fetch(`${API_URL}/api/news/korea?query=증시 특징주`);
            const koreaData = await resKorea.json();
            if (koreaData && koreaData.items) setKoreaNewsList(koreaData.items);
        } catch (err) {} finally { setIsLoadingNews(false); }
    };

    useEffect(() => { 
        fetchUserData();
        fetchAssetHistory('1W');
        fetchStockBatch(0); 
        fetchNewsData();
        fetch(`${API_URL}/api/stock/symbols`).then(r=>r.json()).then(d => { if(Array.isArray(d)) setAllSymbols(d); }).catch(()=>{});
    }, []);

    useEffect(() => {
        fetchAssetHistory(assetHistoryRange);
    }, [assetHistoryRange]);

    useEffect(() => {
        const fetchWatchlistPrices = async () => {
            const updatedData: Record<string, { c: number, d: number, dp: number }> = {};
            const symbolsToFetch = Array.from(new Set([...DASHBOARD_SYMBOLS, ...portfolio.map(p => p.symbol)]));
            for (const sym of symbolsToFetch) {
                try {
                    const res = await fetch(`${API_URL}/api/stock/quote?symbol=${sym}`);
                    const data = await res.json();
                    if (data && data.c) updatedData[sym] = { c: data.c, d: data.d, dp: data.dp };
                } catch (err) {}
            }
            setWatchlistData(prev => ({ ...prev, ...updatedData }));
        };
        fetchWatchlistPrices();
        const interval = setInterval(fetchWatchlistPrices, 30000);
        return () => clearInterval(interval);
    }, [portfolio]);

    useEffect(() => {
        let stockAssets = 0;
        portfolio.forEach(p => stockAssets += (p.amount * (watchlistData[p.symbol]?.c || p.averagePrice)));
        setTotalAssets(balance + stockAssets);
    }, [watchlistData, portfolio, balance]);

    useEffect(() => {
        fetch(`${API_URL}/api/stock/quote?symbol=${selectedSymbol}`).then(r=>r.json()).then(data => {
            if (data && data.c) { setCurrentPrice(data.c); setPriceChange(data.d); setPercentChange(data.dp); }
        }).catch(()=>{});
    }, [selectedSymbol]);

    const getThumbnail = (url: string, originalImage?: string) => {
        const lowerUrl = url.toLowerCase();
        if (lowerUrl.includes('yahoo.com')) return 'https://s.yimg.com/rz/p/yahoo_frontpage_en-US_s_f_p_bestfit_frontpage_2x.png';
        if (lowerUrl.includes('reuters.com')) return 'https://www.reuters.com/pf/resources/images/reuters/logo-vertical-default.svg?d=169';
        if (lowerUrl.includes('bloomberg.com')) return 'https://assets.bwbx.io/s3/javelin/public/hub/images/bloomberg-logo-01-f10d0f507b.png';
        if (lowerUrl.includes('cnbc.com')) return 'https://searchlogovector.com/wp-content/uploads/2018/06/cnbc-logo-vector.png';
        if (lowerUrl.includes('hankyung.com')) return 'https://static.hankyung.com/resource/www/img/logo/logo_hk_p.png';
        if (lowerUrl.includes('mk.co.kr')) return 'https://file.mk.co.kr/mklab/design/logo_mk.png';
        if (lowerUrl.includes('mt.co.kr')) return 'https://image.mt.co.kr/renew/logo_mt.png';
        if (lowerUrl.includes('naver.com')) return 'https://s.pstatic.net/static/www/mobile/edit/2016/0705/mobile_212852414260.png';
        if (originalImage && originalImage.trim() !== '' && !originalImage.includes('yahoo')) return originalImage;
        return 'https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?q=80&w=1000&auto=format&fit=crop';
    };

    const getValidAmount = () => (typeof tradeAmount === 'number' && tradeAmount > 0 ? tradeAmount : 1);
    
    const filteredSymbols = allSymbols.filter(s => s.symbol.toLowerCase().includes(searchTerm.toLowerCase()) || s.description.toLowerCase().includes(searchTerm.toLowerCase())).slice(0, 10);
    const top4Movers = Object.entries(watchlistData).sort(([, a], [, b]) => Math.abs(b.dp) - Math.abs(a.dp)).slice(0, 4);
    const usernameStr = localStorage.getItem('username') || 'Guest';

    const currentNewsArray = newsTab === 'global' ? globalNewsList : koreaNewsList;
    const visibleNews = currentNewsArray.slice(newsIndex, newsIndex + 4);

    // 💡 차트용 데이터 가공 (현금 + 보유 주식)
    const CHART_COLORS = ['#10b981', '#8b5cf6', '#f59e0b', '#f43f5e', '#3b82f6'];
    const assetData = [
        { name: 'Cash', value: balance, color: '#0ea5e9' },
        ...portfolio.map((p, index) => {
            const livePrice = watchlistData[p.symbol]?.c || p.averagePrice;
            return {
                name: p.symbol,
                value: p.amount * livePrice,
                color: CHART_COLORS[index % CHART_COLORS.length]
            };
        })
    ].filter(item => item.value > 0);

    return (
        <div className="min-h-screen bg-[#0b1120] text-slate-200 p-4 sm:p-6 md:p-8 font-sans flex flex-col relative">
            <div className="fixed bottom-8 right-8 z-[100] flex flex-col gap-3 pointer-events-none">
                <AnimatePresence>
                    {toasts.map(toast => (
                        <motion.div key={toast.id} initial={{ opacity: 0, x: 50, scale: 0.9 }} animate={{ opacity: 1, x: 0, scale: 1 }} exit={{ opacity: 0, x: 20, scale: 0.9 }} className="bg-slate-800/90 backdrop-blur-md border border-slate-600 shadow-2xl rounded-2xl p-4 flex items-center gap-4 min-w-[280px]">
                            <div className={`w-12 h-12 rounded-full flex items-center justify-center text-2xl ${toast.type === 'TRANSFER' ? 'bg-indigo-500/20 text-indigo-400' : toast.type === 'BUY' ? 'bg-emerald-500/20 text-emerald-400' : 'bg-rose-500/20 text-rose-400'}`}>
                                {toast.type === 'TRANSFER' ? '💸' : toast.type === 'BUY' ? '📥' : '📤'}
                            </div>
                            <div>
                                <div className="text-sm font-extrabold text-slate-200">{toast.type === 'TRANSFER' ? '입금 알림' : `주식 ${toast.type === 'BUY' ? '매수' : '매도'} 체결`}</div>
                                <div className="text-xs text-slate-400 mt-1">{toast.customMessage ? <span>{toast.customMessage}</span> : <><span className="font-bold text-sky-400">{toast.symbol}</span> {toast.amount}주 @ ${toast.price?.toFixed(2)}</>}</div>
                            </div>
                        </motion.div>
                    ))}
                </AnimatePresence>
            </div>

            <AnimatePresence>
                {isPortfolioModalOpen && (
                    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="fixed inset-0 bg-black/70 z-[80] flex items-center justify-center p-4 backdrop-blur-sm" onClick={() => setIsPortfolioModalOpen(false)}>
                        <motion.div initial={{ scale: 0.9, y: 20 }} animate={{ scale: 1, y: 0 }} exit={{ scale: 0.9, y: 20 }} className="bg-slate-800/90 backdrop-blur-xl border border-slate-600 rounded-3xl w-full max-w-3xl p-6 shadow-2xl relative" onClick={(e) => e.stopPropagation()}>
                            <div className="flex justify-between items-center mb-6 border-b border-slate-700 pb-4">
                                <h2 className="text-2xl font-extrabold text-sky-400">💼 내 포트폴리오 상세</h2>
                                <button onClick={() => setIsPortfolioModalOpen(false)} className="text-slate-400 hover:text-white text-2xl font-bold">&times;</button>
                            </div>
                            <div className="overflow-x-auto max-h-[50vh] custom-scrollbar">
                                <table className="w-full text-left border-collapse min-w-[500px]">
                                    <thead>
                                        <tr className="border-b border-slate-700 text-slate-400 text-sm">
                                            <th className="pb-3 pl-2">종목명</th><th className="pb-3 text-right">보유 수량</th><th className="pb-3 text-right">매수 평단가</th><th className="pb-3 text-right">현재가</th><th className="pb-3 text-right pr-2">평가 수익률</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {portfolio.map((p, idx) => {
                                            const livePrice = watchlistData[p.symbol]?.c || p.averagePrice;
                                            const profit = (livePrice - p.averagePrice) * p.amount;
                                            const isUp = profit >= 0;
                                            return (
                                                <tr key={idx} onClick={() => { setSelectedSymbol(p.symbol); setIsPortfolioModalOpen(false); }} className="border-b border-slate-700/50 hover:bg-slate-700/50 hover:ring-1 hover:ring-sky-500 cursor-pointer transition-all">
                                                    <td className="py-4 pl-2 font-bold text-lg">{p.symbol}</td><td className="py-4 text-right font-mono">{p.amount} 주</td><td className="py-4 text-right font-mono">${p.averagePrice.toFixed(2)}</td><td className="py-4 text-right font-mono text-slate-200">${livePrice.toFixed(2)}</td>
                                                    <td className={`py-4 text-right font-bold pr-2 ${isUp ? 'text-emerald-400' : 'text-rose-400'}`}>{isUp ? '+' : ''}{profit.toFixed(2)} ({isUp ? '+' : ''}{((profit/(p.averagePrice*p.amount))*100).toFixed(2)}%)</td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                        </motion.div>
                    </motion.div>
                )}
            </AnimatePresence>

            <header className="mb-6 flex flex-col sm:flex-row justify-between items-start sm:items-end border-b border-slate-800 pb-4 gap-4">
                <div>
                    <h1 className="text-3xl font-extrabold text-white">환영합니다, <span className="text-transparent bg-clip-text bg-gradient-to-r from-sky-400 to-indigo-400">{usernameStr}</span>님</h1>
                    <p className="text-slate-400 mt-2 text-sm">오늘의 시장 동향과 내 포트폴리오를 확인하세요.</p>
                </div>
                <div className="flex items-center gap-3 w-full sm:w-auto">
                    <button
                        onClick={() => setIsEconomyModalOpen(true)}
                        className="flex items-center gap-2 px-4 py-2 rounded-xl bg-gradient-to-r from-amber-500 via-orange-500 to-pink-500 hover:from-amber-400 hover:to-pink-400 text-white font-extrabold text-xs shadow-lg shadow-orange-500/20 active:scale-95 transition-all cursor-pointer"
                    >
                        <Gift className="w-4 h-4 text-amber-200" />
                        데일리 혜택 & 출석
                    </button>
                    <div className="flex bg-slate-800/50 rounded-lg p-1 border border-slate-700 shadow-md backdrop-blur-md cursor-pointer select-none flex-1 sm:flex-initial" onClick={() => setPanelMode(panelMode === 'summary' ? 'top4' : 'summary')}>
                        <div className="relative flex items-center w-full sm:w-48 h-8 rounded-md">
                            <motion.div className="absolute top-0 bottom-0 w-1/2 bg-sky-500 rounded-md shadow-md" layout transition={{ type: "spring", stiffness: 500, damping: 30 }} initial={false} animate={{ left: panelMode === 'summary' ? "0%" : "50%" }} />
                            <span className={`flex-1 text-center text-xs font-bold z-10 ${panelMode === 'summary' ? 'text-white' : 'text-slate-500'}`}>내 요약</span>
                            <span className={`flex-1 text-center text-xs font-bold z-10 ${panelMode === 'top4' ? 'text-white' : 'text-slate-500'}`}>시장 Top 4</span>
                        </div>
                    </div>
                </div>
            </header>

            <EconomyModal
                isOpen={isEconomyModalOpen}
                onClose={() => setIsEconomyModalOpen(false)}
                onBalanceUpdate={(newBal) => setBalance(newBal)}
                currentBalance={balance}
            />

            <div className="w-full bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-5 sm:p-6 mb-6 shadow-lg">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-5">
                    <div>
                        <h2 className="text-lg font-extrabold text-white">Portfolio Value History</h2>
                        <p className="text-xs text-slate-500 mt-1">
                            기능 도입 이후 실제 총자산 스냅샷을 최대 1시간 간격으로 기록합니다.
                        </p>
                    </div>
                    <div className="flex bg-slate-900 rounded-lg p-1 border border-slate-700">
                        {(['1D', '1W', '1M', 'ALL'] as const).map(range => (
                            <button
                                key={range}
                                type="button"
                                onClick={() => setAssetHistoryRange(range)}
                                className={`px-3 py-1.5 rounded-md text-xs font-bold transition-colors ${assetHistoryRange === range
                                    ? 'bg-sky-500 text-white'
                                    : 'text-slate-400 hover:text-white'}`}
                            >
                                {range}
                            </button>
                        ))}
                    </div>
                </div>

                <div className="w-full h-64">
                    {isAssetHistoryLoading ? (
                        <div className="h-full flex items-center justify-center text-slate-500 text-sm">
                            자산 추이를 불러오는 중...
                        </div>
                    ) : assetHistory.length === 0 ? (
                        <div className="h-full flex items-center justify-center text-slate-500 text-sm text-center px-4">
                            아직 저장된 자산 스냅샷이 없습니다. 대시보드를 이용하면 실제 데이터가 쌓이기 시작합니다.
                        </div>
                    ) : (
                        <ResponsiveContainer width="100%" height="100%">
                            <AreaChart data={assetHistory}>
                                <defs>
                                    <linearGradient id="assetHistoryFill" x1="0" y1="0" x2="0" y2="1">
                                        <stop offset="5%" stopColor="#38bdf8" stopOpacity={0.35} />
                                        <stop offset="95%" stopColor="#38bdf8" stopOpacity={0.02} />
                                    </linearGradient>
                                </defs>
                                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                                <XAxis
                                    dataKey="capturedAt"
                                    tickFormatter={(value: string) => {
                                        const date = new Date(value + (/[zZ]|[+-]\d{2}:\d{2}$/.test(value) ? '' : '+09:00'));
                                        return Number.isNaN(date.getTime())
                                            ? ''
                                            : new Intl.DateTimeFormat('ko-KR', {
                                                timeZone: 'Asia/Seoul',
                                                month: '2-digit',
                                                day: '2-digit',
                                                hour: '2-digit',
                                                minute: '2-digit',
                                                hour12: false
                                            }).format(date);
                                    }}
                                    tick={{ fill: '#64748b', fontSize: 10 }}
                                    minTickGap={28}
                                />
                                <YAxis
                                    width={72}
                                    tickFormatter={(value: number) => '            <div className="w-full grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
                {panelMode === 'summary' ? (
                    <>
                        <div className={`bg-slate-800/50 backdrop-blur-md border ${totalAssets < 100 ? 'border-rose-500/50 ring-1 ring-rose-500/50' : 'border-slate-700/50'} rounded-2xl p-5 flex flex-col justify-between shadow-lg`}>
                            <span className="text-slate-400 text-xs font-bold uppercase">Total Assets</span>
                            <div className="text-2xl font-mono font-bold mt-2 text-white">${totalAssets.toFixed(2)}</div>
                            <div className="flex justify-between items-center mt-1">
                                <span className="text-xs text-sky-400">Cash: ${balance.toFixed(2)}</span>
                                {totalAssets < 100 && (
                                    <button 
                                        onClick={async () => {
                                            try {
                                                const res = await fetch(`${API_URL}/api/trade/relief`, {
                                                    method: 'POST',
                                                    headers: getAuthHeaders()
                                                });
                                                const data = await res.json();
                                                alert(data.message);
                                                if (data.status === 'SUCCESS') fetchUserData();
                                            } catch(e) { alert('오류가 발생했습니다.'); }
                                        }}
                                        className="bg-rose-500 hover:bg-rose-600 text-white text-[10px] font-bold px-2 py-1 rounded shadow animate-pulse"
                                    >
                                        🆘 파산 구제금 신청
                                    </button>
                                )}
                            </div>
                        </div>
                        <div className="bg-slate-800/50 backdrop-blur-md border border-slate-700/50 rounded-2xl p-5 flex flex-col justify-between shadow-lg"><span className="text-slate-400 text-xs font-bold uppercase">Market Sentiment</span><div className="text-xl font-mono font-bold mt-2 text-emerald-400 flex justify-between"><span>BUY {buyRatio}%</span><span className="text-rose-400">SELL {100 - buyRatio}%</span></div><div className="w-full bg-slate-700 h-2 rounded-full mt-2 overflow-hidden flex"><div className="bg-emerald-500 h-full" style={{ width: `${buyRatio}%` }}></div><div className="bg-rose-500 h-full" style={{ width: `${100 - buyRatio}%` }}></div></div></div>
                        <div className="bg-slate-800/50 backdrop-blur-md border border-slate-700/50 rounded-2xl p-5 flex flex-col justify-between shadow-lg"><span className="text-slate-400 text-xs font-bold uppercase">{selectedSymbol} Daily Change</span><div className={`text-2xl font-mono font-bold mt-2 ${percentChange >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>{percentChange >= 0 ? '+' : ''}{percentChange.toFixed(2)}%</div><span className="text-xs text-slate-400 mt-1">Current: ${currentPrice.toFixed(2)}</span></div>
                        <div className="bg-slate-800/50 backdrop-blur-md border border-slate-700/50 rounded-2xl p-5 flex flex-col justify-between shadow-lg cursor-pointer hover:bg-slate-700 hover:border-sky-500 transition-all group" onClick={() => setIsPortfolioModalOpen(true)}>
                            <div className="flex justify-between items-center"><span className="text-slate-400 text-xs font-bold uppercase">Portfolio Holdings</span><span className="text-xs text-sky-400 opacity-0 group-hover:opacity-100 transition-opacity">상세보기 ➔</span></div>
                            <div className="text-2xl mt-2"><span className="font-extrabold text-transparent bg-clip-text bg-gradient-to-r from-sky-400 to-indigo-400">{portfolio.length} 종목</span><span className="font-bold text-slate-300 text-lg ml-1">보유중</span></div><span className="text-xs text-slate-400 mt-1">Active Trading Mode</span>
                        </div>
                    </>
                ) : (
                    top4Movers.map(([sym, data]) => (
                        <div key={sym} onClick={() => setSelectedSymbol(sym)} className="bg-slate-800/50 backdrop-blur-md border border-slate-700/50 rounded-2xl p-5 flex flex-col justify-between shadow-lg cursor-pointer hover:bg-slate-700 hover:border-sky-500 transition-all">
                            <span className="text-slate-400 text-xs font-bold uppercase">{sym}</span><div className="text-2xl font-mono font-bold mt-2 text-white">${data.c.toFixed(2)}</div><span className={`text-xs mt-1 font-bold ${data.d >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>{data.d >= 0 ? '+' : ''}{data.d.toFixed(2)} ({data.dp >= 0 ? '+' : ''}{data.dp.toFixed(2)}%)</span>
                        </div>
                    ))
                )}
            </div>

            <div className="w-full grid grid-cols-1 lg:grid-cols-3 gap-6">
                <div className="col-span-2 flex flex-col gap-6">
                    <div className="relative z-40 bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-4 flex flex-col sm:flex-row sm:items-center shadow-lg w-full gap-3">
                        <span className="text-sm font-bold text-slate-400 flex items-center gap-2 whitespace-nowrap"><Search className="w-4 h-4"/> 종목 검색:</span>
                        <div className="relative w-full">
                            <input type="text" placeholder="회사명 또는 티커 (예: APPLE, AAPL)..." value={searchTerm} onChange={(e) => { setSearchTerm(e.target.value); setIsDropdownOpen(true); }} onFocus={() => setIsDropdownOpen(true)} className="w-full bg-slate-900/80 text-white px-4 py-2.5 rounded-xl border border-slate-700 outline-none focus:border-sky-500 font-mono transition-colors uppercase" />
                            {isDropdownOpen && searchTerm && (
                                <div className="absolute top-14 left-0 right-0 bg-slate-800 border border-slate-600 rounded-xl shadow-2xl max-h-60 overflow-y-auto">
                                    {filteredSymbols.map((item) => (
                                        <div key={item.symbol} onClick={() => { setSelectedSymbol(item.symbol); setSearchTerm(''); setIsDropdownOpen(false); }} className="px-4 py-3 hover:bg-slate-700 cursor-pointer flex justify-between items-center border-b border-slate-700/50">
                                            <span className="font-bold text-sky-400">{item.symbol}</span><span className="text-xs text-slate-300 truncate max-w-[200px]">{item.description}</span>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>

                    <div className="bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-5 shadow-lg relative z-30">
                        <div className="flex justify-between items-center mb-4 border-b border-slate-700 pb-2">
                            <h2 className="text-lg font-bold text-slate-200 flex items-center gap-2"><Briefcase className="w-5 h-5 text-sky-400"/> 해외 주요 증시</h2>
                            <button onClick={() => { const nextIdx = (stockIndex + BATCH_SIZE) % DASHBOARD_SYMBOLS.length; setStockIndex(nextIdx); fetchStockBatch(nextIdx); }} className="flex items-center gap-1 text-xs text-sky-400 bg-sky-500/10 hover:bg-sky-500/20 px-3 py-1.5 rounded-lg transition-colors font-bold">
                                <RefreshCw className={`w-3 h-3 ${isLoadingStocks ? 'animate-spin' : ''}`} /> 다른 종목 보기
                            </button>
                        </div>
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                            {stocks.map((stock, index) => (
                                <div 
                                    key={index} 
                                    onClick={() => !stock.error && setSelectedSymbol(stock.symbol)} 
                                    className={`bg-slate-900/50 p-3 rounded-xl border flex flex-col transition-all ${
                                        stock.error ? 'opacity-50 border-slate-700' : selectedSymbol === stock.symbol ? 'border-sky-500 bg-slate-800 ring-2 ring-sky-500/30' : 'border-slate-700 cursor-pointer hover:border-sky-500 hover:bg-slate-800'
                                    }`}
                                >
                                    <span className="font-black text-sm text-white mb-1">{stock.symbol}</span>
                                    {stock.error ? <span className="text-xs text-slate-500">API 대기</span> : (
                                        <><span className="font-mono font-bold text-slate-300 text-sm">${stock.c?.toFixed(2)}</span><span className={`text-xs font-bold mt-1 ${stock.d > 0 ? 'text-emerald-400' : 'text-rose-400'}`}>{stock.d > 0 ? '+' : ''}{stock.dp?.toFixed(2)}%</span></>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>

                    <div className="bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-6 shadow-lg flex flex-col relative z-20">
                        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center mb-4 gap-3">
                            <div>
                                <h2 className="text-lg font-bold text-slate-200">{selectedSymbol} / USD Real-time</h2>
                                <div className="mt-1 flex items-baseline gap-3">
                                    <span className="text-3xl font-bold font-mono">{currentPrice > 0 ? `$${currentPrice.toFixed(2)}` : 'Loading...'}</span>
                                    <span className={`font-semibold ${priceChange >= 0 ? 'text-emerald-500' : 'text-rose-500'}`}>{priceChange > 0 ? '+' : ''}{priceChange.toFixed(2)} ({percentChange > 0 ? '+' : ''}{percentChange.toFixed(2)}%)</span>
                                </div>
                            </div>
                            <div className="flex gap-2 bg-slate-900 p-1 rounded-lg border border-slate-700 w-full sm:w-auto">
                                <button onClick={() => setResolution('D')} className={`flex-1 sm:flex-none px-3 py-1 rounded text-sm font-bold transition-colors ${resolution === 'D' ? 'bg-sky-500 text-white shadow-md' : 'text-slate-400'}`}>일봉</button>
                                <button onClick={() => setResolution('W')} className={`flex-1 sm:flex-none px-3 py-1 rounded text-sm font-bold transition-colors ${resolution === 'W' ? 'bg-sky-500 text-white shadow-md' : 'text-slate-400'}`}>주봉</button>
                                <button onClick={() => setResolution('M')} className={`flex-1 sm:flex-none px-3 py-1 rounded text-sm font-bold transition-colors ${resolution === 'M' ? 'bg-sky-500 text-white shadow-md' : 'text-slate-400'}`}>월봉</button>
                            </div>
                        </div>
                        <div className="w-full h-[400px] bg-slate-900 rounded-xl overflow-hidden border border-slate-800 mt-2">
                            <iframe key={`${selectedSymbol}-${resolution}`} src={`https://s.tradingview.com/widgetembed/?symbol=${selectedSymbol}&interval=${resolution === 'D' ? 'D' : resolution === 'W' ? 'W' : 'M'}&theme=dark&style=1&hide_top_toolbar=1&hide_side_toolbar=1&withdateranges=1&saveimage=0&locale=kr`} className="w-full h-full border-0" allowTransparency={true} />
                        </div>
                    </div>
                </div>

                <div className="flex flex-col gap-6 relative z-10">
                    <div className="bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-6 shadow-lg">
                        <h2 className="text-xl font-bold mb-4 border-b border-slate-700 pb-2">My Assets</h2>
                        
                        {/* 💡 Recharts 도넛 파이 차트 추가 */}
                        <div className="w-full h-48 my-4 relative">
                            {assetData.length > 0 ? (
                                <ResponsiveContainer width="100%" height="100%">
                                    <PieChart>
                                        <Pie data={assetData} dataKey="value" nameKey="name" cx="50%" cy="50%" innerRadius={50} outerRadius={80} stroke="none" paddingAngle={3}>
                                            {assetData.map((entry, index) => (
                                                <Cell key={`cell-${index}`} fill={entry.color} />
                                            ))}
                                        </Pie>
                                            <RechartsTooltip 
                                                formatter={(value: any, name: any) => [
                                                    `$${Number(value).toFixed(2)} (${((Number(value) / (totalAssets || 1)) * 100).toFixed(1)}%)`,
                                                    name === 'Cash' ? '💵 보유 현금 (Cash)' : `📈 ${name} (보유 주식)`
                                                ]}
                                                contentStyle={{ backgroundColor: '#1e293b', borderColor: '#334155', borderRadius: '0.75rem', color: '#f8fafc' }}
                                                itemStyle={{ fontWeight: 'bold' }}
                                            />
                                    </PieChart>
                                </ResponsiveContainer>
                            ) : (
                                <div className="absolute inset-0 flex items-center justify-center text-slate-500 text-sm font-bold">
                                    자산 정보가 없습니다.
                                </div>
                            )}
                        </div>

                        <div className="mb-4">
                            <p className="text-slate-400 text-xs font-bold uppercase tracking-wider">Total Assets (총 자산)</p>
                            <p className="text-3xl font-mono font-black text-white">${totalAssets.toFixed(2)}</p>
                        </div>

                        {/* 💡 보유 자산 상세 포트폴리오 리스트 (색상 닷 & 종목별 가치 표시) */}
                        <div className="space-y-2 max-h-56 overflow-y-auto custom-scrollbar pr-1">
                            <div className="flex items-center justify-between p-2.5 rounded-xl bg-slate-900/60 border border-slate-700/60">
                                <div className="flex items-center gap-2.5">
                                    <span className="w-3 h-3 rounded-full bg-[#0ea5e9] shadow-sm"></span>
                                    <span className="text-xs font-bold text-slate-200">보유 현금 (Cash)</span>
                                </div>
                                <div className="text-right">
                                    <span className="text-xs font-mono font-bold text-sky-400">${balance.toFixed(2)}</span>
                                    <span className="text-[10px] text-slate-400 block font-mono">
                                        {((balance / (totalAssets || 1)) * 100).toFixed(1)}%
                                    </span>
                                </div>
                            </div>

                            {portfolio.map((p, index) => {
                                const livePrice = watchlistData[p.symbol]?.c || p.averagePrice;
                                const value = p.amount * livePrice;
                                const profit = (livePrice - p.averagePrice) * p.amount;
                                const isUp = profit >= 0;
                                const color = CHART_COLORS[index % CHART_COLORS.length];
                                return (
                                    <div 
                                        key={p.symbol} 
                                        onClick={() => setSelectedSymbol(p.symbol)}
                                        className="flex items-center justify-between p-2.5 rounded-xl bg-slate-900/40 hover:bg-slate-900 border border-slate-800 hover:border-sky-500/50 cursor-pointer transition-all"
                                    >
                                        <div className="flex items-center gap-2.5">
                                            <span className="w-3 h-3 rounded-full shadow-sm" style={{ backgroundColor: color }}></span>
                                            <div>
                                                <span className="text-xs font-bold text-white block">{p.symbol}</span>
                                                <span className="text-[10px] text-slate-400 font-mono">{p.amount}주 @ ${livePrice.toFixed(2)}</span>
                                            </div>
                                        </div>
                                        <div className="text-right">
                                            <span className="text-xs font-mono font-bold text-white">${value.toFixed(2)}</span>
                                            <span className={`text-[10px] block font-mono font-bold ${isUp ? 'text-emerald-400' : 'text-rose-400'}`}>
                                                {isUp ? '+' : ''}{profit.toFixed(2)} ({((value / (totalAssets || 1)) * 100).toFixed(1)}%)
                                            </span>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </div>

                    <div className="bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-6 shadow-lg">
                        <div className="mb-4">
                            <label className="block text-slate-400 text-sm mb-2 font-bold">Quantity (수량)</label>
                            <input type="number" min="1" value={tradeAmount} onChange={(e) => { const val = e.target.value; if (val === '') setTradeAmount(''); else { const parsed = parseInt(val, 10); if (!isNaN(parsed) && parsed > 0) setTradeAmount(parsed); } }} className="w-full bg-slate-900 text-white border border-slate-700 rounded-xl px-4 py-3 outline-none focus:border-sky-500 font-mono text-lg transition-colors shadow-inner" />
                        </div>
                        <div className="flex flex-col sm:flex-row gap-3">
                            <button className="flex-1 bg-rose-500 hover:bg-rose-600 text-white font-bold py-3 px-4 rounded-xl transition shadow-lg"
                                onClick={async () => {
                                    const username = localStorage.getItem('username');
                                    if (!username) return alert("로그인이 필요합니다.");
                                    const amt = getValidAmount();
                                    const res = await fetch(`${API_URL}/api/trade/sell`, { method: 'POST', headers: getAuthHeaders(), body: JSON.stringify({ symbol: selectedSymbol, amount: amt }) });
                                    const data = await res.json();
                                    if (res.ok && data.status === "SUCCESS") { fetchUserData(); showLocalToast('SELL', selectedSymbol, amt, currentPrice); } else alert(data.message);
                                }}
                            >SELL {selectedSymbol}</button>
                            <button className="flex-1 bg-emerald-500 hover:bg-emerald-600 text-white font-bold py-3 px-4 rounded-xl transition shadow-lg"
                                onClick={async () => {
                                    const username = localStorage.getItem('username');
                                    if (!username) return alert("로그인이 필요합니다.");
                                    const amt = getValidAmount();
                                    const res = await fetch(`${API_URL}/api/trade/buy`, { method: 'POST', headers: getAuthHeaders(), body: JSON.stringify({ symbol: selectedSymbol, amount: amt }) });
                                    const data = await res.json();
                                    if (res.ok && data.status === "SUCCESS") { fetchUserData(); showLocalToast('BUY', selectedSymbol, amt, currentPrice); } else alert(data.message);
                                }}
                            >BUY {selectedSymbol}</button>
                        </div>
                    </div>

                    <div className="bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-6 flex-1 max-h-[350px] flex flex-col shadow-lg">
                        <h2 className="text-xl font-bold mb-4 border-b border-slate-700 pb-2">History</h2>
                        <div className="overflow-y-auto pr-2 space-y-3 custom-scrollbar">
                            {history.length === 0 ? <p className="text-slate-500 text-center mt-6 text-sm font-bold">거래 내역이 없습니다.</p> : (
                                history.slice().reverse().map((item) => (
                                    <div key={item.id} className="bg-slate-900/50 border border-slate-700/50 p-3 rounded-xl flex justify-between items-center text-sm hover:border-sky-500/50 transition-colors">
                                        <div><span className={`font-black mr-2 ${item.tradeType === 'BUY' ? 'text-emerald-400' : 'text-rose-400'}`}>{item.tradeType}</span><span className="font-bold text-white">{item.symbol}</span><span className="text-slate-400 ml-1">x {item.amount}</span></div>
                                        <div className="font-mono font-bold text-sky-100">${item.price.toFixed(2)}</div>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                </div>
            </div>

            <div className="w-full bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-6 mt-6 mb-8 shadow-lg">
                <div className="flex justify-between items-center mb-6 border-b border-slate-700 pb-4">
                    <div className="flex items-center gap-2">
                        <Newspaper className="w-6 h-6 text-indigo-400" />
                        <h3 className="text-xl font-bold text-white">Market News</h3>
                    </div>
                    <div className="flex items-center gap-4">
                        <div className="flex bg-slate-900 rounded-full p-1 border border-slate-700 text-sm font-bold">
                            <button onClick={() => { setNewsTab('global'); setNewsIndex(0); }} className={`px-4 py-1 rounded-full transition-colors ${newsTab === 'global' ? 'bg-sky-500 text-white' : 'text-slate-400 hover:text-white'}`}>해외</button>
                            <button onClick={() => { setNewsTab('korea'); setNewsIndex(0); }} className={`px-4 py-1 rounded-full transition-colors ${newsTab === 'korea' ? 'bg-sky-500 text-white' : 'text-slate-400 hover:text-white'}`}>국내</button>
                        </div>
                        <button 
                            onClick={() => setNewsIndex((prev) => (prev + 4) % (currentNewsArray.length || 1))}
                            className="flex items-center gap-1 text-sm text-sky-400 bg-sky-500/10 hover:bg-sky-500/20 px-3 py-1.5 rounded-lg transition-colors font-bold"
                        >
                            <RefreshCw className={`w-4 h-4 ${isLoadingNews ? 'animate-spin' : ''}`} /> 다른 뉴스 보기
                        </button>
                    </div>
                </div>
                
                {isLoadingNews ? (
                    <div className="text-center text-slate-500 py-10 font-bold">뉴스를 불러오는 중입니다...</div>
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                        {newsTab === 'global' ? (
                            visibleNews.length > 0 ? visibleNews.map((news: any, idx: number) => {
                                const imgSrc = getThumbnail(news.url, news.image);
                                const isLogo = !imgSrc.includes('unsplash');
                                return (
                                    <a key={idx} href={news.url} target="_blank" rel="noopener noreferrer" className="block bg-slate-900/50 p-4 rounded-xl border border-slate-700/50 hover:border-sky-500/50 transition-colors group">
                                        <div className={`w-full h-32 mb-3 rounded-lg overflow-hidden bg-slate-800 flex items-center justify-center ${isLogo ? 'p-4' : ''}`}>
                                            <img src={imgSrc} alt="news" className={`w-full h-full ${isLogo ? 'object-contain' : 'object-cover'} group-hover:scale-105 transition-transform duration-300`} onError={(e) => { e.currentTarget.src = '/no-image.png'; }} />
                                        </div>
                                        <h4 className="font-bold text-sm text-slate-100 line-clamp-2 mb-1.5 leading-snug group-hover:text-sky-400 transition-colors">
                                            {news.headline}
                                        </h4>
                                        <p className="text-xs text-slate-400 line-clamp-2 leading-relaxed">
                                            {news.summary}
                                        </p>
                                    </a>
                                );
                            }) : <div className="col-span-full text-center text-slate-500 py-4 font-bold">표시할 해외 뉴스가 없습니다.</div>
                        ) : (
                            visibleNews.length > 0 ? visibleNews.map((news: any, idx: number) => {
                                const imgSrc = getThumbnail(news.link);
                                const isLogo = !imgSrc.includes('unsplash');
                                return (
                                    <a key={idx} href={news.link} target="_blank" rel="noopener noreferrer" className="block bg-slate-900/50 p-4 rounded-xl border border-slate-700/50 hover:border-sky-500/50 transition-colors group">
                                        <div className={`w-full h-32 mb-3 rounded-lg overflow-hidden bg-slate-800 flex items-center justify-center ${isLogo ? 'p-4' : ''}`}>
                                            <img src={imgSrc} alt="news" className={`w-full h-full ${isLogo ? 'object-contain' : 'object-cover'} group-hover:scale-105 transition-transform duration-300`} onError={(e) => { e.currentTarget.src = '/no-image.png'; }} />
                                        </div>
                                        <h4 className="font-bold text-sm text-slate-100 line-clamp-2 mb-1.5 leading-snug group-hover:text-sky-400 transition-colors" dangerouslySetInnerHTML={{ __html: news.title.replace(/<[^>]*>?/gm, '') }} />
                                        <p className="text-xs text-slate-400 line-clamp-2 leading-relaxed" dangerouslySetInnerHTML={{ __html: news.description.replace(/<[^>]*>?/gm, '') }} />
                                    </a>
                                );
                            }) : <div className="col-span-full text-center text-slate-500 py-4 font-bold">표시할 국내 뉴스가 없습니다.</div>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
} + Math.round(value).toLocaleString()}
                                    tick={{ fill: '#64748b', fontSize: 10 }}
                                    domain={['auto', 'auto']}
                                />
                                <RechartsTooltip
                                    formatter={(value: any, name: any) => [
                                        '            <div className="w-full grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
                {panelMode === 'summary' ? (
                    <>
                        <div className={`bg-slate-800/50 backdrop-blur-md border ${totalAssets < 100 ? 'border-rose-500/50 ring-1 ring-rose-500/50' : 'border-slate-700/50'} rounded-2xl p-5 flex flex-col justify-between shadow-lg`}>
                            <span className="text-slate-400 text-xs font-bold uppercase">Total Assets</span>
                            <div className="text-2xl font-mono font-bold mt-2 text-white">${totalAssets.toFixed(2)}</div>
                            <div className="flex justify-between items-center mt-1">
                                <span className="text-xs text-sky-400">Cash: ${balance.toFixed(2)}</span>
                                {totalAssets < 100 && (
                                    <button 
                                        onClick={async () => {
                                            try {
                                                const res = await fetch(`${API_URL}/api/trade/relief`, {
                                                    method: 'POST',
                                                    headers: getAuthHeaders()
                                                });
                                                const data = await res.json();
                                                alert(data.message);
                                                if (data.status === 'SUCCESS') fetchUserData();
                                            } catch(e) { alert('오류가 발생했습니다.'); }
                                        }}
                                        className="bg-rose-500 hover:bg-rose-600 text-white text-[10px] font-bold px-2 py-1 rounded shadow animate-pulse"
                                    >
                                        🆘 파산 구제금 신청
                                    </button>
                                )}
                            </div>
                        </div>
                        <div className="bg-slate-800/50 backdrop-blur-md border border-slate-700/50 rounded-2xl p-5 flex flex-col justify-between shadow-lg"><span className="text-slate-400 text-xs font-bold uppercase">Market Sentiment</span><div className="text-xl font-mono font-bold mt-2 text-emerald-400 flex justify-between"><span>BUY {buyRatio}%</span><span className="text-rose-400">SELL {100 - buyRatio}%</span></div><div className="w-full bg-slate-700 h-2 rounded-full mt-2 overflow-hidden flex"><div className="bg-emerald-500 h-full" style={{ width: `${buyRatio}%` }}></div><div className="bg-rose-500 h-full" style={{ width: `${100 - buyRatio}%` }}></div></div></div>
                        <div className="bg-slate-800/50 backdrop-blur-md border border-slate-700/50 rounded-2xl p-5 flex flex-col justify-between shadow-lg"><span className="text-slate-400 text-xs font-bold uppercase">{selectedSymbol} Daily Change</span><div className={`text-2xl font-mono font-bold mt-2 ${percentChange >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>{percentChange >= 0 ? '+' : ''}{percentChange.toFixed(2)}%</div><span className="text-xs text-slate-400 mt-1">Current: ${currentPrice.toFixed(2)}</span></div>
                        <div className="bg-slate-800/50 backdrop-blur-md border border-slate-700/50 rounded-2xl p-5 flex flex-col justify-between shadow-lg cursor-pointer hover:bg-slate-700 hover:border-sky-500 transition-all group" onClick={() => setIsPortfolioModalOpen(true)}>
                            <div className="flex justify-between items-center"><span className="text-slate-400 text-xs font-bold uppercase">Portfolio Holdings</span><span className="text-xs text-sky-400 opacity-0 group-hover:opacity-100 transition-opacity">상세보기 ➔</span></div>
                            <div className="text-2xl mt-2"><span className="font-extrabold text-transparent bg-clip-text bg-gradient-to-r from-sky-400 to-indigo-400">{portfolio.length} 종목</span><span className="font-bold text-slate-300 text-lg ml-1">보유중</span></div><span className="text-xs text-slate-400 mt-1">Active Trading Mode</span>
                        </div>
                    </>
                ) : (
                    top4Movers.map(([sym, data]) => (
                        <div key={sym} onClick={() => setSelectedSymbol(sym)} className="bg-slate-800/50 backdrop-blur-md border border-slate-700/50 rounded-2xl p-5 flex flex-col justify-between shadow-lg cursor-pointer hover:bg-slate-700 hover:border-sky-500 transition-all">
                            <span className="text-slate-400 text-xs font-bold uppercase">{sym}</span><div className="text-2xl font-mono font-bold mt-2 text-white">${data.c.toFixed(2)}</div><span className={`text-xs mt-1 font-bold ${data.d >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>{data.d >= 0 ? '+' : ''}{data.d.toFixed(2)} ({data.dp >= 0 ? '+' : ''}{data.dp.toFixed(2)}%)</span>
                        </div>
                    ))
                )}
            </div>

            <div className="w-full grid grid-cols-1 lg:grid-cols-3 gap-6">
                <div className="col-span-2 flex flex-col gap-6">
                    <div className="relative z-40 bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-4 flex flex-col sm:flex-row sm:items-center shadow-lg w-full gap-3">
                        <span className="text-sm font-bold text-slate-400 flex items-center gap-2 whitespace-nowrap"><Search className="w-4 h-4"/> 종목 검색:</span>
                        <div className="relative w-full">
                            <input type="text" placeholder="회사명 또는 티커 (예: APPLE, AAPL)..." value={searchTerm} onChange={(e) => { setSearchTerm(e.target.value); setIsDropdownOpen(true); }} onFocus={() => setIsDropdownOpen(true)} className="w-full bg-slate-900/80 text-white px-4 py-2.5 rounded-xl border border-slate-700 outline-none focus:border-sky-500 font-mono transition-colors uppercase" />
                            {isDropdownOpen && searchTerm && (
                                <div className="absolute top-14 left-0 right-0 bg-slate-800 border border-slate-600 rounded-xl shadow-2xl max-h-60 overflow-y-auto">
                                    {filteredSymbols.map((item) => (
                                        <div key={item.symbol} onClick={() => { setSelectedSymbol(item.symbol); setSearchTerm(''); setIsDropdownOpen(false); }} className="px-4 py-3 hover:bg-slate-700 cursor-pointer flex justify-between items-center border-b border-slate-700/50">
                                            <span className="font-bold text-sky-400">{item.symbol}</span><span className="text-xs text-slate-300 truncate max-w-[200px]">{item.description}</span>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>

                    <div className="bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-5 shadow-lg relative z-30">
                        <div className="flex justify-between items-center mb-4 border-b border-slate-700 pb-2">
                            <h2 className="text-lg font-bold text-slate-200 flex items-center gap-2"><Briefcase className="w-5 h-5 text-sky-400"/> 해외 주요 증시</h2>
                            <button onClick={() => { const nextIdx = (stockIndex + BATCH_SIZE) % DASHBOARD_SYMBOLS.length; setStockIndex(nextIdx); fetchStockBatch(nextIdx); }} className="flex items-center gap-1 text-xs text-sky-400 bg-sky-500/10 hover:bg-sky-500/20 px-3 py-1.5 rounded-lg transition-colors font-bold">
                                <RefreshCw className={`w-3 h-3 ${isLoadingStocks ? 'animate-spin' : ''}`} /> 다른 종목 보기
                            </button>
                        </div>
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                            {stocks.map((stock, index) => (
                                <div 
                                    key={index} 
                                    onClick={() => !stock.error && setSelectedSymbol(stock.symbol)} 
                                    className={`bg-slate-900/50 p-3 rounded-xl border flex flex-col transition-all ${
                                        stock.error ? 'opacity-50 border-slate-700' : selectedSymbol === stock.symbol ? 'border-sky-500 bg-slate-800 ring-2 ring-sky-500/30' : 'border-slate-700 cursor-pointer hover:border-sky-500 hover:bg-slate-800'
                                    }`}
                                >
                                    <span className="font-black text-sm text-white mb-1">{stock.symbol}</span>
                                    {stock.error ? <span className="text-xs text-slate-500">API 대기</span> : (
                                        <><span className="font-mono font-bold text-slate-300 text-sm">${stock.c?.toFixed(2)}</span><span className={`text-xs font-bold mt-1 ${stock.d > 0 ? 'text-emerald-400' : 'text-rose-400'}`}>{stock.d > 0 ? '+' : ''}{stock.dp?.toFixed(2)}%</span></>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>

                    <div className="bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-6 shadow-lg flex flex-col relative z-20">
                        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center mb-4 gap-3">
                            <div>
                                <h2 className="text-lg font-bold text-slate-200">{selectedSymbol} / USD Real-time</h2>
                                <div className="mt-1 flex items-baseline gap-3">
                                    <span className="text-3xl font-bold font-mono">{currentPrice > 0 ? `$${currentPrice.toFixed(2)}` : 'Loading...'}</span>
                                    <span className={`font-semibold ${priceChange >= 0 ? 'text-emerald-500' : 'text-rose-500'}`}>{priceChange > 0 ? '+' : ''}{priceChange.toFixed(2)} ({percentChange > 0 ? '+' : ''}{percentChange.toFixed(2)}%)</span>
                                </div>
                            </div>
                            <div className="flex gap-2 bg-slate-900 p-1 rounded-lg border border-slate-700 w-full sm:w-auto">
                                <button onClick={() => setResolution('D')} className={`flex-1 sm:flex-none px-3 py-1 rounded text-sm font-bold transition-colors ${resolution === 'D' ? 'bg-sky-500 text-white shadow-md' : 'text-slate-400'}`}>일봉</button>
                                <button onClick={() => setResolution('W')} className={`flex-1 sm:flex-none px-3 py-1 rounded text-sm font-bold transition-colors ${resolution === 'W' ? 'bg-sky-500 text-white shadow-md' : 'text-slate-400'}`}>주봉</button>
                                <button onClick={() => setResolution('M')} className={`flex-1 sm:flex-none px-3 py-1 rounded text-sm font-bold transition-colors ${resolution === 'M' ? 'bg-sky-500 text-white shadow-md' : 'text-slate-400'}`}>월봉</button>
                            </div>
                        </div>
                        <div className="w-full h-[400px] bg-slate-900 rounded-xl overflow-hidden border border-slate-800 mt-2">
                            <iframe key={`${selectedSymbol}-${resolution}`} src={`https://s.tradingview.com/widgetembed/?symbol=${selectedSymbol}&interval=${resolution === 'D' ? 'D' : resolution === 'W' ? 'W' : 'M'}&theme=dark&style=1&hide_top_toolbar=1&hide_side_toolbar=1&withdateranges=1&saveimage=0&locale=kr`} className="w-full h-full border-0" allowTransparency={true} />
                        </div>
                    </div>
                </div>

                <div className="flex flex-col gap-6 relative z-10">
                    <div className="bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-6 shadow-lg">
                        <h2 className="text-xl font-bold mb-4 border-b border-slate-700 pb-2">My Assets</h2>
                        
                        {/* 💡 Recharts 도넛 파이 차트 추가 */}
                        <div className="w-full h-48 my-4 relative">
                            {assetData.length > 0 ? (
                                <ResponsiveContainer width="100%" height="100%">
                                    <PieChart>
                                        <Pie data={assetData} dataKey="value" nameKey="name" cx="50%" cy="50%" innerRadius={50} outerRadius={80} stroke="none" paddingAngle={3}>
                                            {assetData.map((entry, index) => (
                                                <Cell key={`cell-${index}`} fill={entry.color} />
                                            ))}
                                        </Pie>
                                            <RechartsTooltip 
                                                formatter={(value: any, name: any) => [
                                                    `$${Number(value).toFixed(2)} (${((Number(value) / (totalAssets || 1)) * 100).toFixed(1)}%)`,
                                                    name === 'Cash' ? '💵 보유 현금 (Cash)' : `📈 ${name} (보유 주식)`
                                                ]}
                                                contentStyle={{ backgroundColor: '#1e293b', borderColor: '#334155', borderRadius: '0.75rem', color: '#f8fafc' }}
                                                itemStyle={{ fontWeight: 'bold' }}
                                            />
                                    </PieChart>
                                </ResponsiveContainer>
                            ) : (
                                <div className="absolute inset-0 flex items-center justify-center text-slate-500 text-sm font-bold">
                                    자산 정보가 없습니다.
                                </div>
                            )}
                        </div>

                        <div className="mb-4">
                            <p className="text-slate-400 text-xs font-bold uppercase tracking-wider">Total Assets (총 자산)</p>
                            <p className="text-3xl font-mono font-black text-white">${totalAssets.toFixed(2)}</p>
                        </div>

                        {/* 💡 보유 자산 상세 포트폴리오 리스트 (색상 닷 & 종목별 가치 표시) */}
                        <div className="space-y-2 max-h-56 overflow-y-auto custom-scrollbar pr-1">
                            <div className="flex items-center justify-between p-2.5 rounded-xl bg-slate-900/60 border border-slate-700/60">
                                <div className="flex items-center gap-2.5">
                                    <span className="w-3 h-3 rounded-full bg-[#0ea5e9] shadow-sm"></span>
                                    <span className="text-xs font-bold text-slate-200">보유 현금 (Cash)</span>
                                </div>
                                <div className="text-right">
                                    <span className="text-xs font-mono font-bold text-sky-400">${balance.toFixed(2)}</span>
                                    <span className="text-[10px] text-slate-400 block font-mono">
                                        {((balance / (totalAssets || 1)) * 100).toFixed(1)}%
                                    </span>
                                </div>
                            </div>

                            {portfolio.map((p, index) => {
                                const livePrice = watchlistData[p.symbol]?.c || p.averagePrice;
                                const value = p.amount * livePrice;
                                const profit = (livePrice - p.averagePrice) * p.amount;
                                const isUp = profit >= 0;
                                const color = CHART_COLORS[index % CHART_COLORS.length];
                                return (
                                    <div 
                                        key={p.symbol} 
                                        onClick={() => setSelectedSymbol(p.symbol)}
                                        className="flex items-center justify-between p-2.5 rounded-xl bg-slate-900/40 hover:bg-slate-900 border border-slate-800 hover:border-sky-500/50 cursor-pointer transition-all"
                                    >
                                        <div className="flex items-center gap-2.5">
                                            <span className="w-3 h-3 rounded-full shadow-sm" style={{ backgroundColor: color }}></span>
                                            <div>
                                                <span className="text-xs font-bold text-white block">{p.symbol}</span>
                                                <span className="text-[10px] text-slate-400 font-mono">{p.amount}주 @ ${livePrice.toFixed(2)}</span>
                                            </div>
                                        </div>
                                        <div className="text-right">
                                            <span className="text-xs font-mono font-bold text-white">${value.toFixed(2)}</span>
                                            <span className={`text-[10px] block font-mono font-bold ${isUp ? 'text-emerald-400' : 'text-rose-400'}`}>
                                                {isUp ? '+' : ''}{profit.toFixed(2)} ({((value / (totalAssets || 1)) * 100).toFixed(1)}%)
                                            </span>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </div>

                    <div className="bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-6 shadow-lg">
                        <div className="mb-4">
                            <label className="block text-slate-400 text-sm mb-2 font-bold">Quantity (수량)</label>
                            <input type="number" min="1" value={tradeAmount} onChange={(e) => { const val = e.target.value; if (val === '') setTradeAmount(''); else { const parsed = parseInt(val, 10); if (!isNaN(parsed) && parsed > 0) setTradeAmount(parsed); } }} className="w-full bg-slate-900 text-white border border-slate-700 rounded-xl px-4 py-3 outline-none focus:border-sky-500 font-mono text-lg transition-colors shadow-inner" />
                        </div>
                        <div className="flex flex-col sm:flex-row gap-3">
                            <button className="flex-1 bg-rose-500 hover:bg-rose-600 text-white font-bold py-3 px-4 rounded-xl transition shadow-lg"
                                onClick={async () => {
                                    const username = localStorage.getItem('username');
                                    if (!username) return alert("로그인이 필요합니다.");
                                    const amt = getValidAmount();
                                    const res = await fetch(`${API_URL}/api/trade/sell`, { method: 'POST', headers: getAuthHeaders(), body: JSON.stringify({ symbol: selectedSymbol, amount: amt }) });
                                    const data = await res.json();
                                    if (res.ok && data.status === "SUCCESS") { fetchUserData(); showLocalToast('SELL', selectedSymbol, amt, currentPrice); } else alert(data.message);
                                }}
                            >SELL {selectedSymbol}</button>
                            <button className="flex-1 bg-emerald-500 hover:bg-emerald-600 text-white font-bold py-3 px-4 rounded-xl transition shadow-lg"
                                onClick={async () => {
                                    const username = localStorage.getItem('username');
                                    if (!username) return alert("로그인이 필요합니다.");
                                    const amt = getValidAmount();
                                    const res = await fetch(`${API_URL}/api/trade/buy`, { method: 'POST', headers: getAuthHeaders(), body: JSON.stringify({ symbol: selectedSymbol, amount: amt }) });
                                    const data = await res.json();
                                    if (res.ok && data.status === "SUCCESS") { fetchUserData(); showLocalToast('BUY', selectedSymbol, amt, currentPrice); } else alert(data.message);
                                }}
                            >BUY {selectedSymbol}</button>
                        </div>
                    </div>

                    <div className="bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-6 flex-1 max-h-[350px] flex flex-col shadow-lg">
                        <h2 className="text-xl font-bold mb-4 border-b border-slate-700 pb-2">History</h2>
                        <div className="overflow-y-auto pr-2 space-y-3 custom-scrollbar">
                            {history.length === 0 ? <p className="text-slate-500 text-center mt-6 text-sm font-bold">거래 내역이 없습니다.</p> : (
                                history.slice().reverse().map((item) => (
                                    <div key={item.id} className="bg-slate-900/50 border border-slate-700/50 p-3 rounded-xl flex justify-between items-center text-sm hover:border-sky-500/50 transition-colors">
                                        <div><span className={`font-black mr-2 ${item.tradeType === 'BUY' ? 'text-emerald-400' : 'text-rose-400'}`}>{item.tradeType}</span><span className="font-bold text-white">{item.symbol}</span><span className="text-slate-400 ml-1">x {item.amount}</span></div>
                                        <div className="font-mono font-bold text-sky-100">${item.price.toFixed(2)}</div>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                </div>
            </div>

            <div className="w-full bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-6 mt-6 mb-8 shadow-lg">
                <div className="flex justify-between items-center mb-6 border-b border-slate-700 pb-4">
                    <div className="flex items-center gap-2">
                        <Newspaper className="w-6 h-6 text-indigo-400" />
                        <h3 className="text-xl font-bold text-white">Market News</h3>
                    </div>
                    <div className="flex items-center gap-4">
                        <div className="flex bg-slate-900 rounded-full p-1 border border-slate-700 text-sm font-bold">
                            <button onClick={() => { setNewsTab('global'); setNewsIndex(0); }} className={`px-4 py-1 rounded-full transition-colors ${newsTab === 'global' ? 'bg-sky-500 text-white' : 'text-slate-400 hover:text-white'}`}>해외</button>
                            <button onClick={() => { setNewsTab('korea'); setNewsIndex(0); }} className={`px-4 py-1 rounded-full transition-colors ${newsTab === 'korea' ? 'bg-sky-500 text-white' : 'text-slate-400 hover:text-white'}`}>국내</button>
                        </div>
                        <button 
                            onClick={() => setNewsIndex((prev) => (prev + 4) % (currentNewsArray.length || 1))}
                            className="flex items-center gap-1 text-sm text-sky-400 bg-sky-500/10 hover:bg-sky-500/20 px-3 py-1.5 rounded-lg transition-colors font-bold"
                        >
                            <RefreshCw className={`w-4 h-4 ${isLoadingNews ? 'animate-spin' : ''}`} /> 다른 뉴스 보기
                        </button>
                    </div>
                </div>
                
                {isLoadingNews ? (
                    <div className="text-center text-slate-500 py-10 font-bold">뉴스를 불러오는 중입니다...</div>
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                        {newsTab === 'global' ? (
                            visibleNews.length > 0 ? visibleNews.map((news: any, idx: number) => {
                                const imgSrc = getThumbnail(news.url, news.image);
                                const isLogo = !imgSrc.includes('unsplash');
                                return (
                                    <a key={idx} href={news.url} target="_blank" rel="noopener noreferrer" className="block bg-slate-900/50 p-4 rounded-xl border border-slate-700/50 hover:border-sky-500/50 transition-colors group">
                                        <div className={`w-full h-32 mb-3 rounded-lg overflow-hidden bg-slate-800 flex items-center justify-center ${isLogo ? 'p-4' : ''}`}>
                                            <img src={imgSrc} alt="news" className={`w-full h-full ${isLogo ? 'object-contain' : 'object-cover'} group-hover:scale-105 transition-transform duration-300`} onError={(e) => { e.currentTarget.src = '/no-image.png'; }} />
                                        </div>
                                        <h4 className="font-bold text-sm text-slate-100 line-clamp-2 mb-1.5 leading-snug group-hover:text-sky-400 transition-colors">
                                            {news.headline}
                                        </h4>
                                        <p className="text-xs text-slate-400 line-clamp-2 leading-relaxed">
                                            {news.summary}
                                        </p>
                                    </a>
                                );
                            }) : <div className="col-span-full text-center text-slate-500 py-4 font-bold">표시할 해외 뉴스가 없습니다.</div>
                        ) : (
                            visibleNews.length > 0 ? visibleNews.map((news: any, idx: number) => {
                                const imgSrc = getThumbnail(news.link);
                                const isLogo = !imgSrc.includes('unsplash');
                                return (
                                    <a key={idx} href={news.link} target="_blank" rel="noopener noreferrer" className="block bg-slate-900/50 p-4 rounded-xl border border-slate-700/50 hover:border-sky-500/50 transition-colors group">
                                        <div className={`w-full h-32 mb-3 rounded-lg overflow-hidden bg-slate-800 flex items-center justify-center ${isLogo ? 'p-4' : ''}`}>
                                            <img src={imgSrc} alt="news" className={`w-full h-full ${isLogo ? 'object-contain' : 'object-cover'} group-hover:scale-105 transition-transform duration-300`} onError={(e) => { e.currentTarget.src = '/no-image.png'; }} />
                                        </div>
                                        <h4 className="font-bold text-sm text-slate-100 line-clamp-2 mb-1.5 leading-snug group-hover:text-sky-400 transition-colors" dangerouslySetInnerHTML={{ __html: news.title.replace(/<[^>]*>?/gm, '') }} />
                                        <p className="text-xs text-slate-400 line-clamp-2 leading-relaxed" dangerouslySetInnerHTML={{ __html: news.description.replace(/<[^>]*>?/gm, '') }} />
                                    </a>
                                );
                            }) : <div className="col-span-full text-center text-slate-500 py-4 font-bold">표시할 국내 뉴스가 없습니다.</div>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
} + Number(value).toLocaleString(undefined, {
                                            minimumFractionDigits: 2,
                                            maximumFractionDigits: 2
                                        }),
                                        name === 'totalAssets' ? '총자산' : name
                                    ]}
                                    labelFormatter={(value: any) => String(value).replace('T', ' ')}
                                    contentStyle={{
                                        backgroundColor: '#0f172a',
                                        borderColor: '#334155',
                                        borderRadius: '0.75rem'
                                    }}
                                />
                                <Area
                                    type="monotone"
                                    dataKey="totalAssets"
                                    stroke="#38bdf8"
                                    strokeWidth={2}
                                    fill="url(#assetHistoryFill)"
                                />
                            </AreaChart>
                        </ResponsiveContainer>
                    )}
                </div>
            </div>

            <div className="w-full grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
                {panelMode === 'summary' ? (
                    <>
                        <div className={`bg-slate-800/50 backdrop-blur-md border ${totalAssets < 100 ? 'border-rose-500/50 ring-1 ring-rose-500/50' : 'border-slate-700/50'} rounded-2xl p-5 flex flex-col justify-between shadow-lg`}>
                            <span className="text-slate-400 text-xs font-bold uppercase">Total Assets</span>
                            <div className="text-2xl font-mono font-bold mt-2 text-white">${totalAssets.toFixed(2)}</div>
                            <div className="flex justify-between items-center mt-1">
                                <span className="text-xs text-sky-400">Cash: ${balance.toFixed(2)}</span>
                                {totalAssets < 100 && (
                                    <button 
                                        onClick={async () => {
                                            try {
                                                const res = await fetch(`${API_URL}/api/trade/relief`, {
                                                    method: 'POST',
                                                    headers: getAuthHeaders()
                                                });
                                                const data = await res.json();
                                                alert(data.message);
                                                if (data.status === 'SUCCESS') fetchUserData();
                                            } catch(e) { alert('오류가 발생했습니다.'); }
                                        }}
                                        className="bg-rose-500 hover:bg-rose-600 text-white text-[10px] font-bold px-2 py-1 rounded shadow animate-pulse"
                                    >
                                        🆘 파산 구제금 신청
                                    </button>
                                )}
                            </div>
                        </div>
                        <div className="bg-slate-800/50 backdrop-blur-md border border-slate-700/50 rounded-2xl p-5 flex flex-col justify-between shadow-lg"><span className="text-slate-400 text-xs font-bold uppercase">Market Sentiment</span><div className="text-xl font-mono font-bold mt-2 text-emerald-400 flex justify-between"><span>BUY {buyRatio}%</span><span className="text-rose-400">SELL {100 - buyRatio}%</span></div><div className="w-full bg-slate-700 h-2 rounded-full mt-2 overflow-hidden flex"><div className="bg-emerald-500 h-full" style={{ width: `${buyRatio}%` }}></div><div className="bg-rose-500 h-full" style={{ width: `${100 - buyRatio}%` }}></div></div></div>
                        <div className="bg-slate-800/50 backdrop-blur-md border border-slate-700/50 rounded-2xl p-5 flex flex-col justify-between shadow-lg"><span className="text-slate-400 text-xs font-bold uppercase">{selectedSymbol} Daily Change</span><div className={`text-2xl font-mono font-bold mt-2 ${percentChange >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>{percentChange >= 0 ? '+' : ''}{percentChange.toFixed(2)}%</div><span className="text-xs text-slate-400 mt-1">Current: ${currentPrice.toFixed(2)}</span></div>
                        <div className="bg-slate-800/50 backdrop-blur-md border border-slate-700/50 rounded-2xl p-5 flex flex-col justify-between shadow-lg cursor-pointer hover:bg-slate-700 hover:border-sky-500 transition-all group" onClick={() => setIsPortfolioModalOpen(true)}>
                            <div className="flex justify-between items-center"><span className="text-slate-400 text-xs font-bold uppercase">Portfolio Holdings</span><span className="text-xs text-sky-400 opacity-0 group-hover:opacity-100 transition-opacity">상세보기 ➔</span></div>
                            <div className="text-2xl mt-2"><span className="font-extrabold text-transparent bg-clip-text bg-gradient-to-r from-sky-400 to-indigo-400">{portfolio.length} 종목</span><span className="font-bold text-slate-300 text-lg ml-1">보유중</span></div><span className="text-xs text-slate-400 mt-1">Active Trading Mode</span>
                        </div>
                    </>
                ) : (
                    top4Movers.map(([sym, data]) => (
                        <div key={sym} onClick={() => setSelectedSymbol(sym)} className="bg-slate-800/50 backdrop-blur-md border border-slate-700/50 rounded-2xl p-5 flex flex-col justify-between shadow-lg cursor-pointer hover:bg-slate-700 hover:border-sky-500 transition-all">
                            <span className="text-slate-400 text-xs font-bold uppercase">{sym}</span><div className="text-2xl font-mono font-bold mt-2 text-white">${data.c.toFixed(2)}</div><span className={`text-xs mt-1 font-bold ${data.d >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>{data.d >= 0 ? '+' : ''}{data.d.toFixed(2)} ({data.dp >= 0 ? '+' : ''}{data.dp.toFixed(2)}%)</span>
                        </div>
                    ))
                )}
            </div>

            <div className="w-full grid grid-cols-1 lg:grid-cols-3 gap-6">
                <div className="col-span-2 flex flex-col gap-6">
                    <div className="relative z-40 bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-4 flex flex-col sm:flex-row sm:items-center shadow-lg w-full gap-3">
                        <span className="text-sm font-bold text-slate-400 flex items-center gap-2 whitespace-nowrap"><Search className="w-4 h-4"/> 종목 검색:</span>
                        <div className="relative w-full">
                            <input type="text" placeholder="회사명 또는 티커 (예: APPLE, AAPL)..." value={searchTerm} onChange={(e) => { setSearchTerm(e.target.value); setIsDropdownOpen(true); }} onFocus={() => setIsDropdownOpen(true)} className="w-full bg-slate-900/80 text-white px-4 py-2.5 rounded-xl border border-slate-700 outline-none focus:border-sky-500 font-mono transition-colors uppercase" />
                            {isDropdownOpen && searchTerm && (
                                <div className="absolute top-14 left-0 right-0 bg-slate-800 border border-slate-600 rounded-xl shadow-2xl max-h-60 overflow-y-auto">
                                    {filteredSymbols.map((item) => (
                                        <div key={item.symbol} onClick={() => { setSelectedSymbol(item.symbol); setSearchTerm(''); setIsDropdownOpen(false); }} className="px-4 py-3 hover:bg-slate-700 cursor-pointer flex justify-between items-center border-b border-slate-700/50">
                                            <span className="font-bold text-sky-400">{item.symbol}</span><span className="text-xs text-slate-300 truncate max-w-[200px]">{item.description}</span>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>

                    <div className="bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-5 shadow-lg relative z-30">
                        <div className="flex justify-between items-center mb-4 border-b border-slate-700 pb-2">
                            <h2 className="text-lg font-bold text-slate-200 flex items-center gap-2"><Briefcase className="w-5 h-5 text-sky-400"/> 해외 주요 증시</h2>
                            <button onClick={() => { const nextIdx = (stockIndex + BATCH_SIZE) % DASHBOARD_SYMBOLS.length; setStockIndex(nextIdx); fetchStockBatch(nextIdx); }} className="flex items-center gap-1 text-xs text-sky-400 bg-sky-500/10 hover:bg-sky-500/20 px-3 py-1.5 rounded-lg transition-colors font-bold">
                                <RefreshCw className={`w-3 h-3 ${isLoadingStocks ? 'animate-spin' : ''}`} /> 다른 종목 보기
                            </button>
                        </div>
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                            {stocks.map((stock, index) => (
                                <div 
                                    key={index} 
                                    onClick={() => !stock.error && setSelectedSymbol(stock.symbol)} 
                                    className={`bg-slate-900/50 p-3 rounded-xl border flex flex-col transition-all ${
                                        stock.error ? 'opacity-50 border-slate-700' : selectedSymbol === stock.symbol ? 'border-sky-500 bg-slate-800 ring-2 ring-sky-500/30' : 'border-slate-700 cursor-pointer hover:border-sky-500 hover:bg-slate-800'
                                    }`}
                                >
                                    <span className="font-black text-sm text-white mb-1">{stock.symbol}</span>
                                    {stock.error ? <span className="text-xs text-slate-500">API 대기</span> : (
                                        <><span className="font-mono font-bold text-slate-300 text-sm">${stock.c?.toFixed(2)}</span><span className={`text-xs font-bold mt-1 ${stock.d > 0 ? 'text-emerald-400' : 'text-rose-400'}`}>{stock.d > 0 ? '+' : ''}{stock.dp?.toFixed(2)}%</span></>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>

                    <div className="bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-6 shadow-lg flex flex-col relative z-20">
                        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center mb-4 gap-3">
                            <div>
                                <h2 className="text-lg font-bold text-slate-200">{selectedSymbol} / USD Real-time</h2>
                                <div className="mt-1 flex items-baseline gap-3">
                                    <span className="text-3xl font-bold font-mono">{currentPrice > 0 ? `$${currentPrice.toFixed(2)}` : 'Loading...'}</span>
                                    <span className={`font-semibold ${priceChange >= 0 ? 'text-emerald-500' : 'text-rose-500'}`}>{priceChange > 0 ? '+' : ''}{priceChange.toFixed(2)} ({percentChange > 0 ? '+' : ''}{percentChange.toFixed(2)}%)</span>
                                </div>
                            </div>
                            <div className="flex gap-2 bg-slate-900 p-1 rounded-lg border border-slate-700 w-full sm:w-auto">
                                <button onClick={() => setResolution('D')} className={`flex-1 sm:flex-none px-3 py-1 rounded text-sm font-bold transition-colors ${resolution === 'D' ? 'bg-sky-500 text-white shadow-md' : 'text-slate-400'}`}>일봉</button>
                                <button onClick={() => setResolution('W')} className={`flex-1 sm:flex-none px-3 py-1 rounded text-sm font-bold transition-colors ${resolution === 'W' ? 'bg-sky-500 text-white shadow-md' : 'text-slate-400'}`}>주봉</button>
                                <button onClick={() => setResolution('M')} className={`flex-1 sm:flex-none px-3 py-1 rounded text-sm font-bold transition-colors ${resolution === 'M' ? 'bg-sky-500 text-white shadow-md' : 'text-slate-400'}`}>월봉</button>
                            </div>
                        </div>
                        <div className="w-full h-[400px] bg-slate-900 rounded-xl overflow-hidden border border-slate-800 mt-2">
                            <iframe key={`${selectedSymbol}-${resolution}`} src={`https://s.tradingview.com/widgetembed/?symbol=${selectedSymbol}&interval=${resolution === 'D' ? 'D' : resolution === 'W' ? 'W' : 'M'}&theme=dark&style=1&hide_top_toolbar=1&hide_side_toolbar=1&withdateranges=1&saveimage=0&locale=kr`} className="w-full h-full border-0" allowTransparency={true} />
                        </div>
                    </div>
                </div>

                <div className="flex flex-col gap-6 relative z-10">
                    <div className="bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-6 shadow-lg">
                        <h2 className="text-xl font-bold mb-4 border-b border-slate-700 pb-2">My Assets</h2>
                        
                        {/* 💡 Recharts 도넛 파이 차트 추가 */}
                        <div className="w-full h-48 my-4 relative">
                            {assetData.length > 0 ? (
                                <ResponsiveContainer width="100%" height="100%">
                                    <PieChart>
                                        <Pie data={assetData} dataKey="value" nameKey="name" cx="50%" cy="50%" innerRadius={50} outerRadius={80} stroke="none" paddingAngle={3}>
                                            {assetData.map((entry, index) => (
                                                <Cell key={`cell-${index}`} fill={entry.color} />
                                            ))}
                                        </Pie>
                                            <RechartsTooltip 
                                                formatter={(value: any, name: any) => [
                                                    `$${Number(value).toFixed(2)} (${((Number(value) / (totalAssets || 1)) * 100).toFixed(1)}%)`,
                                                    name === 'Cash' ? '💵 보유 현금 (Cash)' : `📈 ${name} (보유 주식)`
                                                ]}
                                                contentStyle={{ backgroundColor: '#1e293b', borderColor: '#334155', borderRadius: '0.75rem', color: '#f8fafc' }}
                                                itemStyle={{ fontWeight: 'bold' }}
                                            />
                                    </PieChart>
                                </ResponsiveContainer>
                            ) : (
                                <div className="absolute inset-0 flex items-center justify-center text-slate-500 text-sm font-bold">
                                    자산 정보가 없습니다.
                                </div>
                            )}
                        </div>

                        <div className="mb-4">
                            <p className="text-slate-400 text-xs font-bold uppercase tracking-wider">Total Assets (총 자산)</p>
                            <p className="text-3xl font-mono font-black text-white">${totalAssets.toFixed(2)}</p>
                        </div>

                        {/* 💡 보유 자산 상세 포트폴리오 리스트 (색상 닷 & 종목별 가치 표시) */}
                        <div className="space-y-2 max-h-56 overflow-y-auto custom-scrollbar pr-1">
                            <div className="flex items-center justify-between p-2.5 rounded-xl bg-slate-900/60 border border-slate-700/60">
                                <div className="flex items-center gap-2.5">
                                    <span className="w-3 h-3 rounded-full bg-[#0ea5e9] shadow-sm"></span>
                                    <span className="text-xs font-bold text-slate-200">보유 현금 (Cash)</span>
                                </div>
                                <div className="text-right">
                                    <span className="text-xs font-mono font-bold text-sky-400">${balance.toFixed(2)}</span>
                                    <span className="text-[10px] text-slate-400 block font-mono">
                                        {((balance / (totalAssets || 1)) * 100).toFixed(1)}%
                                    </span>
                                </div>
                            </div>

                            {portfolio.map((p, index) => {
                                const livePrice = watchlistData[p.symbol]?.c || p.averagePrice;
                                const value = p.amount * livePrice;
                                const profit = (livePrice - p.averagePrice) * p.amount;
                                const isUp = profit >= 0;
                                const color = CHART_COLORS[index % CHART_COLORS.length];
                                return (
                                    <div 
                                        key={p.symbol} 
                                        onClick={() => setSelectedSymbol(p.symbol)}
                                        className="flex items-center justify-between p-2.5 rounded-xl bg-slate-900/40 hover:bg-slate-900 border border-slate-800 hover:border-sky-500/50 cursor-pointer transition-all"
                                    >
                                        <div className="flex items-center gap-2.5">
                                            <span className="w-3 h-3 rounded-full shadow-sm" style={{ backgroundColor: color }}></span>
                                            <div>
                                                <span className="text-xs font-bold text-white block">{p.symbol}</span>
                                                <span className="text-[10px] text-slate-400 font-mono">{p.amount}주 @ ${livePrice.toFixed(2)}</span>
                                            </div>
                                        </div>
                                        <div className="text-right">
                                            <span className="text-xs font-mono font-bold text-white">${value.toFixed(2)}</span>
                                            <span className={`text-[10px] block font-mono font-bold ${isUp ? 'text-emerald-400' : 'text-rose-400'}`}>
                                                {isUp ? '+' : ''}{profit.toFixed(2)} ({((value / (totalAssets || 1)) * 100).toFixed(1)}%)
                                            </span>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </div>

                    <div className="bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-6 shadow-lg">
                        <div className="mb-4">
                            <label className="block text-slate-400 text-sm mb-2 font-bold">Quantity (수량)</label>
                            <input type="number" min="1" value={tradeAmount} onChange={(e) => { const val = e.target.value; if (val === '') setTradeAmount(''); else { const parsed = parseInt(val, 10); if (!isNaN(parsed) && parsed > 0) setTradeAmount(parsed); } }} className="w-full bg-slate-900 text-white border border-slate-700 rounded-xl px-4 py-3 outline-none focus:border-sky-500 font-mono text-lg transition-colors shadow-inner" />
                        </div>
                        <div className="flex flex-col sm:flex-row gap-3">
                            <button className="flex-1 bg-rose-500 hover:bg-rose-600 text-white font-bold py-3 px-4 rounded-xl transition shadow-lg"
                                onClick={async () => {
                                    const username = localStorage.getItem('username');
                                    if (!username) return alert("로그인이 필요합니다.");
                                    const amt = getValidAmount();
                                    const res = await fetch(`${API_URL}/api/trade/sell`, { method: 'POST', headers: getAuthHeaders(), body: JSON.stringify({ symbol: selectedSymbol, amount: amt }) });
                                    const data = await res.json();
                                    if (res.ok && data.status === "SUCCESS") { fetchUserData(); showLocalToast('SELL', selectedSymbol, amt, currentPrice); } else alert(data.message);
                                }}
                            >SELL {selectedSymbol}</button>
                            <button className="flex-1 bg-emerald-500 hover:bg-emerald-600 text-white font-bold py-3 px-4 rounded-xl transition shadow-lg"
                                onClick={async () => {
                                    const username = localStorage.getItem('username');
                                    if (!username) return alert("로그인이 필요합니다.");
                                    const amt = getValidAmount();
                                    const res = await fetch(`${API_URL}/api/trade/buy`, { method: 'POST', headers: getAuthHeaders(), body: JSON.stringify({ symbol: selectedSymbol, amount: amt }) });
                                    const data = await res.json();
                                    if (res.ok && data.status === "SUCCESS") { fetchUserData(); showLocalToast('BUY', selectedSymbol, amt, currentPrice); } else alert(data.message);
                                }}
                            >BUY {selectedSymbol}</button>
                        </div>
                    </div>

                    <div className="bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-6 flex-1 max-h-[350px] flex flex-col shadow-lg">
                        <h2 className="text-xl font-bold mb-4 border-b border-slate-700 pb-2">History</h2>
                        <div className="overflow-y-auto pr-2 space-y-3 custom-scrollbar">
                            {history.length === 0 ? <p className="text-slate-500 text-center mt-6 text-sm font-bold">거래 내역이 없습니다.</p> : (
                                history.slice().reverse().map((item) => (
                                    <div key={item.id} className="bg-slate-900/50 border border-slate-700/50 p-3 rounded-xl flex justify-between items-center text-sm hover:border-sky-500/50 transition-colors">
                                        <div><span className={`font-black mr-2 ${item.tradeType === 'BUY' ? 'text-emerald-400' : 'text-rose-400'}`}>{item.tradeType}</span><span className="font-bold text-white">{item.symbol}</span><span className="text-slate-400 ml-1">x {item.amount}</span></div>
                                        <div className="font-mono font-bold text-sky-100">${item.price.toFixed(2)}</div>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                </div>
            </div>

            <div className="w-full bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 rounded-2xl p-6 mt-6 mb-8 shadow-lg">
                <div className="flex justify-between items-center mb-6 border-b border-slate-700 pb-4">
                    <div className="flex items-center gap-2">
                        <Newspaper className="w-6 h-6 text-indigo-400" />
                        <h3 className="text-xl font-bold text-white">Market News</h3>
                    </div>
                    <div className="flex items-center gap-4">
                        <div className="flex bg-slate-900 rounded-full p-1 border border-slate-700 text-sm font-bold">
                            <button onClick={() => { setNewsTab('global'); setNewsIndex(0); }} className={`px-4 py-1 rounded-full transition-colors ${newsTab === 'global' ? 'bg-sky-500 text-white' : 'text-slate-400 hover:text-white'}`}>해외</button>
                            <button onClick={() => { setNewsTab('korea'); setNewsIndex(0); }} className={`px-4 py-1 rounded-full transition-colors ${newsTab === 'korea' ? 'bg-sky-500 text-white' : 'text-slate-400 hover:text-white'}`}>국내</button>
                        </div>
                        <button 
                            onClick={() => setNewsIndex((prev) => (prev + 4) % (currentNewsArray.length || 1))}
                            className="flex items-center gap-1 text-sm text-sky-400 bg-sky-500/10 hover:bg-sky-500/20 px-3 py-1.5 rounded-lg transition-colors font-bold"
                        >
                            <RefreshCw className={`w-4 h-4 ${isLoadingNews ? 'animate-spin' : ''}`} /> 다른 뉴스 보기
                        </button>
                    </div>
                </div>
                
                {isLoadingNews ? (
                    <div className="text-center text-slate-500 py-10 font-bold">뉴스를 불러오는 중입니다...</div>
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                        {newsTab === 'global' ? (
                            visibleNews.length > 0 ? visibleNews.map((news: any, idx: number) => {
                                const imgSrc = getThumbnail(news.url, news.image);
                                const isLogo = !imgSrc.includes('unsplash');
                                return (
                                    <a key={idx} href={news.url} target="_blank" rel="noopener noreferrer" className="block bg-slate-900/50 p-4 rounded-xl border border-slate-700/50 hover:border-sky-500/50 transition-colors group">
                                        <div className={`w-full h-32 mb-3 rounded-lg overflow-hidden bg-slate-800 flex items-center justify-center ${isLogo ? 'p-4' : ''}`}>
                                            <img src={imgSrc} alt="news" className={`w-full h-full ${isLogo ? 'object-contain' : 'object-cover'} group-hover:scale-105 transition-transform duration-300`} onError={(e) => { e.currentTarget.src = '/no-image.png'; }} />
                                        </div>
                                        <h4 className="font-bold text-sm text-slate-100 line-clamp-2 mb-1.5 leading-snug group-hover:text-sky-400 transition-colors">
                                            {news.headline}
                                        </h4>
                                        <p className="text-xs text-slate-400 line-clamp-2 leading-relaxed">
                                            {news.summary}
                                        </p>
                                    </a>
                                );
                            }) : <div className="col-span-full text-center text-slate-500 py-4 font-bold">표시할 해외 뉴스가 없습니다.</div>
                        ) : (
                            visibleNews.length > 0 ? visibleNews.map((news: any, idx: number) => {
                                const imgSrc = getThumbnail(news.link);
                                const isLogo = !imgSrc.includes('unsplash');
                                return (
                                    <a key={idx} href={news.link} target="_blank" rel="noopener noreferrer" className="block bg-slate-900/50 p-4 rounded-xl border border-slate-700/50 hover:border-sky-500/50 transition-colors group">
                                        <div className={`w-full h-32 mb-3 rounded-lg overflow-hidden bg-slate-800 flex items-center justify-center ${isLogo ? 'p-4' : ''}`}>
                                            <img src={imgSrc} alt="news" className={`w-full h-full ${isLogo ? 'object-contain' : 'object-cover'} group-hover:scale-105 transition-transform duration-300`} onError={(e) => { e.currentTarget.src = '/no-image.png'; }} />
                                        </div>
                                        <h4 className="font-bold text-sm text-slate-100 line-clamp-2 mb-1.5 leading-snug group-hover:text-sky-400 transition-colors" dangerouslySetInnerHTML={{ __html: news.title.replace(/<[^>]*>?/gm, '') }} />
                                        <p className="text-xs text-slate-400 line-clamp-2 leading-relaxed" dangerouslySetInnerHTML={{ __html: news.description.replace(/<[^>]*>?/gm, '') }} />
                                    </a>
                                );
                            }) : <div className="col-span-full text-center text-slate-500 py-4 font-bold">표시할 국내 뉴스가 없습니다.</div>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
}