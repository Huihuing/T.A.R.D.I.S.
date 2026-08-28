import { useEffect, useState, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { createChart, ColorType, CandlestickSeries } from 'lightweight-charts';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

interface TradeHistory { id: number; tradeType: string; symbol: string; amount: number; price: number; tradeTime: string; }
interface PortfolioItem { symbol: string; amount: number; averagePrice: number; }
interface NewsItem { id: number; headline: string; summary: string; url: string; image: string; }
interface NaverNewsItem { title: string; link: string; description: string; pubDate: string; }
interface StockSymbol { symbol: string; description: string; displaySymbol: string; }
interface TradeToast { id: number; type: 'BUY' | 'SELL' | 'TRANSFER'; symbol?: string; amount?: number; price?: number; customMessage?: string; }

const WATCHLIST = ['AAPL', 'TSLA', 'MSFT', 'NVDA', 'AMD', 'META', 'AMZN', 'GOOGL'];

export default function Dashboard() {
    const [balance, setBalance] = useState<number>(0);
    const [history, setHistory] = useState<TradeHistory[]>([]);
    const [portfolio, setPortfolio] = useState<PortfolioItem[]>([]);
    const [tradeAmount, setTradeAmount] = useState<number | ''>(1);
    const [newsTab, setNewsTab] = useState<'global' | 'korea'>('global'); 
    const [koreaNewsList, setKoreaNewsList] = useState<NaverNewsItem[]>([]);
    const [newsList, setNewsList] = useState<NewsItem[]>([]); 
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
    const [toasts, setToasts] = useState<TradeToast[]>([]);
    const chartContainerRef = useRef<HTMLDivElement>(null);

    // 공통 JWT 헤더 생성 함수
    const getAuthHeaders = () => {
        const token = localStorage.getItem('token');
        return {
            'Content-Type': 'application/json',
            ...(token && { 'Authorization': `Bearer ${token}` })
        };
    };

    // 🚀 웹소켓 (STOMP) 연결 및 알림 구독
    useEffect(() => {
        const username = localStorage.getItem('username');
        if (!username) return;

        const stompClient = new Client({
            webSocketFactory: () => new SockJS('http://localhost:8080/ws-stomp'),
            reconnectDelay: 5000,
            onConnect: () => {
                stompClient.subscribe(`/topic/alerts/${username}`, (message) => {
                    const data = JSON.parse(message.body);
                    const toastId = Date.now();
                    setToasts(prev => [...prev, { id: toastId, type: data.type, customMessage: data.message }]);
                    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== toastId)), 4000);
                    fetchUserData(); // 알림 오면 잔고 및 내역 갱신
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
            const balRes = await fetch(`http://localhost:8080/api/trade/balance?username=${username}`, { headers });
            const balNum = Number(await balRes.text());
            setBalance(isNaN(balNum) ? 0 : balNum);
            
            const histRes = await fetch(`http://localhost:8080/api/trade/history?username=${username}`, { headers });
            const histData: TradeHistory[] = await histRes.json();
            setHistory(histData);

            if (histData.length > 0) {
                const buyCount = histData.filter(h => h.tradeType === 'BUY').length;
                setBuyRatio(Math.round((buyCount / histData.length) * 100));
            }
            
            const portRes = await fetch(`http://localhost:8080/api/trade/portfolio?username=${username}`, { headers });
            setPortfolio(await portRes.json());
        } catch (err) {}
    };

    useEffect(() => { 
        fetchUserData(); 
        const fetchAllSymbols = async () => {
            try {
                const res = await fetch('http://localhost:8080/api/stock/symbols');
                const data = await res.json();
                if (Array.isArray(data)) setAllSymbols(data);
            } catch (err) {}
        };
        fetchAllSymbols();
    }, []);

    useEffect(() => {
        const fetchWatchlistPrices = async () => {
            const updatedData: Record<string, { c: number, d: number, dp: number }> = {};
            const symbolsToFetch = Array.from(new Set([...WATCHLIST, ...portfolio.map(p => p.symbol)]));
            for (const sym of symbolsToFetch) {
                try {
                    const res = await fetch(`http://localhost:8080/api/stock/quote?symbol=${sym}`);
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
        portfolio.forEach(p => {
            const currentP = watchlistData[p.symbol]?.c || p.averagePrice; 
            stockAssets += (p.amount * currentP);
        });
        setTotalAssets(balance + stockAssets);
    }, [watchlistData, portfolio, balance]);

    useEffect(() => {
        const fetchNews = async () => {
            try {
                const resGlobal = await fetch(`http://localhost:8080/api/news/global?symbol=${selectedSymbol}`);
                const globalData = await resGlobal.json();
                if (Array.isArray(globalData)) setNewsList(globalData);
                const resKorea = await fetch('http://localhost:8080/api/news/korea?query=증시 특징주');
                const koreaData = await resKorea.json();
                if (koreaData && koreaData.items) setKoreaNewsList(koreaData.items);
            } catch (err) {}
        };
        fetchNews();
    }, [selectedSymbol]);

    useEffect(() => {
        const fetchMainStockDetail = async () => {
            try {
                const res = await fetch(`http://localhost:8080/api/stock/quote?symbol=${selectedSymbol}`);
                const data = await res.json();
                if (data && data.c) { setCurrentPrice(data.c); setPriceChange(data.d); setPercentChange(data.dp); }
            } catch (error) {}
        };
        fetchMainStockDetail();
    }, [selectedSymbol]);

    useEffect(() => {
        if (!chartContainerRef.current) return;
        const chart = createChart(chartContainerRef.current, {
            layout: { background: { type: ColorType.Solid, color: 'transparent' }, textColor: '#94a3b8' },
            grid: { vertLines: { color: '#334155' }, horzLines: { color: '#334155' } },
            width: chartContainerRef.current.clientWidth,
            height: 280,
            localization: { dateFormat: 'yyyy-MM-dd' },
            timeScale: { timeVisible: false, rightOffset: 5 },
        });

        const candlestickSeries = chart.addSeries(CandlestickSeries, { upColor: '#22c55e', downColor: '#ef4444', borderVisible: false, wickUpColor: '#22c55e', wickDownColor: '#ef4444' });

        const fetchCandles = async () => {
            try {
                const res = await fetch(`http://localhost:8080/api/stock/candles?symbol=${selectedSymbol}&resolution=${resolution}`);
                const data = await res.json();
                if (data.chart && data.chart.result && data.chart.result[0]) {
                    const result = data.chart.result[0];
                    const timestamps = result.timestamp;
                    const quotes = result.indicators.quote[0];
                    if (timestamps && quotes) {
                        const formattedData = timestamps.map((time: number, index: number) => ({
                            time: time, open: quotes.open[index], high: quotes.high[index], low: quotes.low[index], close: quotes.close[index]
                        })).filter((item: any) => item.open !== null && item.close !== null);
                        candlestickSeries.setData(formattedData);
                        const dataLength = formattedData.length;
                        if (dataLength > 60) chart.timeScale().setVisibleLogicalRange({ from: dataLength - 60, to: dataLength });
                        else chart.timeScale().fitContent();
                    }
                }
            } catch (error) {}
        };
        fetchCandles();
        const handleResize = () => { if (chartContainerRef.current) chart.applyOptions({ width: chartContainerRef.current.clientWidth }); };
        window.addEventListener('resize', handleResize);
        return () => { window.removeEventListener('resize', handleResize); chart.remove(); };
    }, [selectedSymbol, resolution]);

    const myStock = portfolio.find(p => p.symbol === selectedSymbol);
    let unrealizedProfit = 0; let profitRate = 0;
    if (myStock && currentPrice > 0) {
        unrealizedProfit = (myStock.amount * currentPrice) - (myStock.amount * myStock.averagePrice);
        profitRate = (unrealizedProfit / (myStock.amount * myStock.averagePrice)) * 100;
    }

    const getValidAmount = () => (typeof tradeAmount === 'number' && tradeAmount > 0 ? tradeAmount : 1);
    const filteredSymbols = allSymbols.filter(s => s.symbol.toLowerCase().includes(searchTerm.toLowerCase()) || s.description.toLowerCase().includes(searchTerm.toLowerCase())).slice(0, 10);
    const top4Movers = Object.entries(watchlistData).sort(([, a], [, b]) => Math.abs(b.dp) - Math.abs(a.dp)).slice(0, 4);

    return (
        <div className="min-h-screen bg-slate-900 text-white p-8 font-sans flex flex-col relative">
            {/* 🚀 토스트 팝업 렌더링 구역 */}
            <div className="fixed bottom-8 right-8 z-[100] flex flex-col gap-3 pointer-events-none">
                <AnimatePresence>
                    {toasts.map(toast => (
                        <motion.div key={toast.id} initial={{ opacity: 0, x: 50, scale: 0.9 }} animate={{ opacity: 1, x: 0, scale: 1 }} exit={{ opacity: 0, x: 20, scale: 0.9 }} className="bg-slate-800 border border-slate-600 shadow-2xl rounded-xl p-4 flex items-center gap-4 min-w-[280px]">
                            <div className={`w-12 h-12 rounded-full flex items-center justify-center text-2xl ${toast.type === 'TRANSFER' ? 'bg-indigo-500/20 text-indigo-400' : toast.type === 'BUY' ? 'bg-green-500/20 text-green-400' : 'bg-red-500/20 text-red-400'}`}>
                                {toast.type === 'TRANSFER' ? '💸' : toast.type === 'BUY' ? '📥' : '📤'}
                            </div>
                            <div>
                                <div className="text-sm font-extrabold text-slate-200">{toast.type === 'TRANSFER' ? '입금 알림' : `주식 ${toast.type === 'BUY' ? '매수' : '매도'} 체결`}</div>
                                <div className="text-xs text-slate-400 mt-1">
                                    {toast.customMessage ? <span>{toast.customMessage}</span> : <><span className="font-bold text-sky-400">{toast.symbol}</span> {toast.amount}주 @ ${toast.price?.toFixed(2)}</>}
                                </div>
                            </div>
                        </motion.div>
                    ))}
                </AnimatePresence>
            </div>

            {/* 🚀 포트폴리오 모달 (클릭 시 퀵 트레이드 연동) */}
            <AnimatePresence>
                {isPortfolioModalOpen && (
                    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="fixed inset-0 bg-black/70 z-50 flex items-center justify-center p-4 backdrop-blur-sm" onClick={() => setIsPortfolioModalOpen(false)}>
                        <motion.div initial={{ scale: 0.9, y: 20 }} animate={{ scale: 1, y: 0 }} exit={{ scale: 0.9, y: 20 }} className="bg-slate-800 border border-slate-600 rounded-2xl w-full max-w-3xl p-6 shadow-2xl relative" onClick={(e) => e.stopPropagation()}>
                            <div className="flex justify-between items-center mb-6 border-b border-slate-700 pb-4">
                                <h2 className="text-2xl font-extrabold text-sky-400">💼 내 포트폴리오 상세</h2>
                                <button onClick={() => setIsPortfolioModalOpen(false)} className="text-slate-400 hover:text-white text-2xl font-bold">&times;</button>
                            </div>
                            <div className="overflow-x-auto max-h-[50vh] custom-scrollbar">
                                <table className="w-full text-left border-collapse">
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
                                                <tr key={idx} onClick={() => { setSelectedSymbol(p.symbol); setIsPortfolioModalOpen(false); }} className="border-b border-slate-700/50 hover:bg-slate-750 hover:ring-1 hover:ring-sky-500 cursor-pointer transition-all">
                                                    <td className="py-4 pl-2 font-bold text-lg">{p.symbol}</td><td className="py-4 text-right font-mono">{p.amount} 주</td><td className="py-4 text-right font-mono">${p.averagePrice.toFixed(2)}</td><td className="py-4 text-right font-mono text-slate-200">${livePrice.toFixed(2)}</td>
                                                    <td className={`py-4 text-right font-bold pr-2 ${isUp ? 'text-green-400' : 'text-red-400'}`}>{isUp ? '+' : ''}{profit.toFixed(2)} ({isUp ? '+' : ''}{((profit/(p.averagePrice*p.amount))*100).toFixed(2)}%)</td>
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

            <header className="mb-6 text-center relative">
                <h1 className="text-4xl font-extrabold text-sky-400">T.A.R.D.I.S.</h1>
                <p className="text-slate-400">Time And Relative Dimension In Stocks</p>
            </header>

            <div className="max-w-6xl mx-auto w-full flex justify-end mb-3">
                <div className="flex items-center bg-slate-800 rounded-full p-1 border border-slate-700 cursor-pointer select-none" onClick={() => setPanelMode(panelMode === 'summary' ? 'top4' : 'summary')}>
                    <div className="relative flex items-center w-48 h-8 rounded-full">
                        <motion.div className="absolute top-0 bottom-0 w-1/2 bg-sky-500 rounded-full shadow-md" layout transition={{ type: "spring", stiffness: 500, damping: 30 }} initial={false} animate={{ left: panelMode === 'summary' ? "0%" : "50%" }} />
                        <span className={`flex-1 text-center text-xs font-bold z-10 ${panelMode === 'summary' ? 'text-white' : 'text-slate-500'}`}>내 요약</span>
                        <span className={`flex-1 text-center text-xs font-bold z-10 ${panelMode === 'top4' ? 'text-white' : 'text-slate-500'}`}>시장 Top 4</span>
                    </div>
                </div>
            </div>

            <div className="max-w-6xl mx-auto w-full grid grid-cols-1 md:grid-cols-4 gap-4 mb-8 h-[104px]">
                {panelMode === 'summary' ? (
                    <>
                        <div className="bg-slate-800 border border-slate-700 rounded-xl p-4 flex flex-col justify-between"><span className="text-slate-400 text-xs font-bold uppercase">Total Assets</span><div className="text-2xl font-mono font-bold mt-2">${totalAssets.toFixed(2)}</div><span className="text-xs text-sky-400 mt-1">Cash: ${balance.toFixed(2)}</span></div>
                        <div className="bg-slate-800 border border-slate-700 rounded-xl p-4 flex flex-col justify-between"><span className="text-slate-400 text-xs font-bold uppercase">Market Sentiment</span><div className="text-xl font-mono font-bold mt-2 text-green-400 flex justify-between"><span>BUY {buyRatio}%</span><span className="text-red-400">SELL {100 - buyRatio}%</span></div><div className="w-full bg-slate-700 h-2 rounded-full mt-2 overflow-hidden flex"><div className="bg-green-500 h-full" style={{ width: `${buyRatio}%` }}></div><div className="bg-red-500 h-full" style={{ width: `${100 - buyRatio}%` }}></div></div></div>
                        <div className="bg-slate-800 border border-slate-700 rounded-xl p-4 flex flex-col justify-between"><span className="text-slate-400 text-xs font-bold uppercase">{selectedSymbol} Daily Change</span><div className={`text-2xl font-mono font-bold mt-2 ${percentChange >= 0 ? 'text-green-400' : 'text-red-400'}`}>{percentChange >= 0 ? '+' : ''}{percentChange.toFixed(2)}%</div><span className="text-xs text-slate-400 mt-1">Current: ${currentPrice.toFixed(2)}</span></div>
                        <div className="bg-slate-800 border border-slate-700 rounded-xl p-4 flex flex-col justify-between cursor-pointer hover:bg-slate-750 hover:border-sky-500 transition-all group" onClick={() => setIsPortfolioModalOpen(true)}><div className="flex justify-between items-center"><span className="text-slate-400 text-xs font-bold uppercase">Portfolio Holdings</span><span className="text-xs text-sky-400 opacity-0 group-hover:opacity-100 transition-opacity">상세보기 ➔</span></div><div className="text-2xl font-mono font-bold mt-2 text-sky-400">{portfolio.length} 종목 보유중</div><span className="text-xs text-slate-400 mt-1">Active Trading Mode</span></div>
                    </>
                ) : (
                    top4Movers.map(([sym, data]) => (
                        <div key={sym} onClick={() => setSelectedSymbol(sym)} className="bg-slate-800 border border-slate-700 rounded-xl p-4 flex flex-col justify-between cursor-pointer hover:bg-slate-750 hover:border-sky-500 transition-all">
                            <span className="text-slate-400 text-xs font-bold uppercase">{sym}</span><div className="text-2xl font-mono font-bold mt-2">${data.c.toFixed(2)}</div><span className={`text-xs mt-1 font-bold ${data.d >= 0 ? 'text-green-400' : 'text-red-400'}`}>{data.d >= 0 ? '+' : ''}{data.d.toFixed(2)} ({data.dp >= 0 ? '+' : ''}{data.dp.toFixed(2)}%)</span>
                        </div>
                    ))
                )}
            </div>

            <div className="max-w-6xl mx-auto w-full grid grid-cols-1 lg:grid-cols-3 gap-8">
                <div className="col-span-2 flex flex-col gap-6">
                    <div className="relative bg-slate-800 border border-slate-700 rounded-xl p-4 flex items-center">
                        <span className="text-sm font-bold text-slate-400 mr-4">🔍 종목 검색:</span>
                        <input type="text" placeholder="종목 코드(AAPL) 입력..." value={searchTerm} onChange={(e) => { setSearchTerm(e.target.value); setIsDropdownOpen(true); }} onFocus={() => setIsDropdownOpen(true)} className="flex-1 bg-slate-900 text-white px-4 py-2 rounded-lg border border-slate-600 outline-none focus:border-sky-500 font-mono" />
                        {isDropdownOpen && searchTerm && (
                            <div className="absolute top-20 left-4 right-4 bg-slate-800 border border-slate-600 rounded-xl shadow-2xl z-50 max-h-60 overflow-y-auto">
                                {filteredSymbols.map((item) => (
                                    <div key={item.symbol} onClick={() => { setSelectedSymbol(item.symbol); setSearchTerm(''); setIsDropdownOpen(false); }} className="px-4 py-3 hover:bg-slate-700 cursor-pointer flex justify-between items-center border-b border-slate-700/50">
                                        <span className="font-bold text-sky-400">{item.symbol}</span><span className="text-xs text-slate-300 truncate">{item.description}</span>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>

                    <div className="bg-slate-800 border border-slate-700 rounded-xl p-6 shadow-lg flex flex-col">
                        <div className="flex justify-between items-start mb-4">
                            <div>
                                <h2 className="text-lg font-bold text-slate-200">{selectedSymbol} / USD Real-time</h2>
                                <div className="mt-1 flex items-baseline gap-3">
                                    <span className="text-3xl font-bold font-mono">{currentPrice > 0 ? `$${currentPrice.toFixed(2)}` : 'Loading...'}</span>
                                    <span className={`font-semibold ${priceChange >= 0 ? 'text-green-500' : 'text-red-500'}`}>{priceChange > 0 ? '+' : ''}{priceChange.toFixed(2)} ({percentChange > 0 ? '+' : ''}{percentChange.toFixed(2)}%)</span>
                                </div>
                            </div>
                            <div className="flex gap-2 bg-slate-900 p-1 rounded-lg border border-slate-700">
                                <button onClick={() => setResolution('D')} className={`px-3 py-1 rounded text-sm font-bold transition-colors ${resolution === 'D' ? 'bg-sky-500 text-white' : 'text-slate-400'}`}>일봉</button>
                                <button onClick={() => setResolution('W')} className={`px-3 py-1 rounded text-sm font-bold transition-colors ${resolution === 'W' ? 'bg-sky-500 text-white' : 'text-slate-400'}`}>주봉</button>
                                <button onClick={() => setResolution('M')} className={`px-3 py-1 rounded text-sm font-bold transition-colors ${resolution === 'M' ? 'bg-sky-500 text-white' : 'text-slate-400'}`}>월봉</button>
                            </div>
                        </div>
                        <div className="w-full h-[280px]" ref={chartContainerRef} />
                    </div>

                    <div className="bg-slate-800 border border-slate-700 rounded-xl p-6 flex-1">
                        <div className="flex justify-between items-center mb-4 border-b border-slate-600 pb-4">
                            <h2 className="text-xl font-bold flex items-center gap-2"><span className="text-sky-400">🔥</span> Market News</h2>
                            <div className="flex items-center bg-slate-900 rounded-full p-1 border border-slate-700 cursor-pointer" onClick={() => setNewsTab(newsTab === 'global' ? 'korea' : 'global')}>
                                <div className="relative flex items-center w-36 h-8 rounded-full">
                                    <motion.div className="absolute top-0 bottom-0 w-1/2 bg-sky-500 rounded-full shadow-md" layout transition={{ type: "spring", stiffness: 500, damping: 30 }} initial={false} animate={{ left: newsTab === 'global' ? "0%" : "50%" }} />
                                    <span className={`flex-1 text-center text-sm font-bold z-10 ${newsTab === 'global' ? 'text-white' : 'text-slate-500'}`}>해외</span>
                                    <span className={`flex-1 text-center text-sm font-bold z-10 ${newsTab === 'korea' ? 'text-white' : 'text-slate-500'}`}>국내</span>
                                </div>
                            </div>
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 max-h-[400px] overflow-y-auto pr-2 custom-scrollbar">
                            {newsTab === 'global' ? (
                                newsList.map((news, idx) => (
                                    <a key={idx} href={news.url} target="_blank" rel="noopener noreferrer" className="flex gap-4 p-3 rounded-lg hover:bg-slate-700 transition border border-transparent hover:border-slate-600">
                                        <img src={news.image || '/no-image.png'} alt="news" className="w-20 h-20 object-cover rounded-md flex-shrink-0 bg-slate-900" onError={(e) => { e.currentTarget.src = '/no-image.png'; }} />
                                        <div className="flex flex-col justify-center">
                                            <h3 className="text-sm font-bold text-slate-200 line-clamp-2 mb-1">{news.headline}</h3>
                                            <p className="text-xs text-slate-400 line-clamp-2">{news.summary}</p>
                                        </div>
                                    </a>
                                ))
                            ) : (
                                koreaNewsList.map((news, idx) => (
                                    <a key={idx} href={news.link} target="_blank" rel="noopener noreferrer" className="bg-slate-800 border border-slate-700 p-4 rounded-xl hover:border-sky-500 transition-all">
                                        <h3 className="text-sm font-bold text-slate-100 line-clamp-2 mb-2" dangerouslySetInnerHTML={{ __html: news.title.replace(/<[^>]*>?/gm, '') }}></h3>
                                        <p className="text-xs text-slate-400 line-clamp-3" dangerouslySetInnerHTML={{ __html: news.description.replace(/<[^>]*>?/gm, '') }}></p>
                                    </a>
                                ))
                            )}
                        </div>
                    </div>
                </div>

                <div className="flex flex-col gap-6">
                    <div className="bg-slate-800 border border-slate-700 rounded-xl p-6">
                        <h2 className="text-xl font-bold mb-4 border-b border-slate-600 pb-2">My Assets</h2>
                        <div className="mb-4"><p className="text-slate-400 text-sm">Total Assets (총 자산)</p><p className="text-3xl font-mono text-white">${totalAssets.toFixed(2)}</p></div>
                        <div className="flex justify-between text-sm mb-2"><span className="text-slate-400">Cash Balance (현금)</span><span className="font-mono text-sky-300">${balance.toFixed(2)}</span></div>
                        
                        {myStock && (
                            <div className="mt-4 p-4 bg-slate-700/50 rounded-lg cursor-pointer hover:bg-slate-700 hover:ring-1 hover:ring-sky-500 transition-all" onClick={() => setSelectedSymbol(myStock.symbol)}>
                                <div className="flex justify-between items-center mb-1"><span className="font-bold text-sky-400">{myStock.symbol} 보유량</span><span className="font-mono text-lg">{myStock.amount} 주</span></div>
                                <div className="flex justify-between items-center text-sm"><span className="text-slate-400">평균 단가</span><span className="font-mono">${myStock.averagePrice.toFixed(2)}</span></div>
                                <div className={`flex justify-between items-center text-sm mt-2 font-bold ${unrealizedProfit >= 0 ? 'text-green-400' : 'text-red-400'}`}><span>평가 손익</span><span className="font-mono">{unrealizedProfit >= 0 ? '+' : ''}{unrealizedProfit.toFixed(2)} ({profitRate.toFixed(2)}%)</span></div>
                                <div className="text-center mt-3 text-xs text-slate-400 font-bold">클릭하여 즉시 매매하기 ➔</div>
                            </div>
                        )}
                    </div>

                    <div className="bg-slate-800 border border-slate-700 rounded-xl p-6">
                        <div className="mb-4">
                            <label className="block text-slate-400 text-sm mb-2 font-bold">Quantity (수량)</label>
                            <input type="number" min="1" value={tradeAmount} onChange={(e) => { const val = e.target.value; if (val === '') setTradeAmount(''); else { const parsed = parseInt(val, 10); if (!isNaN(parsed) && parsed > 0) setTradeAmount(parsed); } }} className="w-full bg-slate-900 text-white border border-slate-600 rounded-lg px-4 py-3 outline-none focus:border-sky-500 font-mono text-lg transition" />
                        </div>
                        <div className="flex gap-4">
                            <button className="flex-1 bg-red-500 hover:bg-red-600 text-white font-bold py-3 px-4 rounded-lg transition shadow-lg"
                                onClick={async () => {
                                    const username = localStorage.getItem('username');
                                    if (!username) return alert("로그인이 필요합니다.");
                                    const amt = getValidAmount();
                                    const res = await fetch(`http://localhost:8080/api/trade/sell?username=${username}`, { method: 'POST', headers: getAuthHeaders(), body: JSON.stringify({ symbol: selectedSymbol, amount: amt, price: currentPrice }) });
                                    const data = await res.json();
                                    if (res.ok && data.status === "SUCCESS") { fetchUserData(); showLocalToast('SELL', selectedSymbol, amt, currentPrice); } else alert(data.message);
                                }}
                            >SELL {selectedSymbol}</button>
                            <button className="flex-1 bg-green-500 hover:bg-green-600 text-white font-bold py-3 px-4 rounded-lg transition shadow-lg"
                                onClick={async () => {
                                    const username = localStorage.getItem('username');
                                    if (!username) return alert("로그인이 필요합니다.");
                                    const amt = getValidAmount();
                                    const res = await fetch(`http://localhost:8080/api/trade/buy?username=${username}`, { method: 'POST', headers: getAuthHeaders(), body: JSON.stringify({ symbol: selectedSymbol, amount: amt, price: currentPrice }) });
                                    const data = await res.json();
                                    if (res.ok && data.status === "SUCCESS") { fetchUserData(); showLocalToast('BUY', selectedSymbol, amt, currentPrice); } else alert(data.message);
                                }}
                            >BUY {selectedSymbol}</button>
                        </div>
                    </div>

                    <div className="bg-slate-800 border border-slate-700 rounded-xl p-6 flex-1 max-h-[300px] flex flex-col">
                        <h2 className="text-xl font-bold mb-4 border-b border-slate-600 pb-2">History</h2>
                        <div className="overflow-y-auto pr-2 space-y-3 custom-scrollbar">
                            {history.length === 0 ? <p className="text-slate-500 text-center mt-4">No History</p> : (
                                history.slice().reverse().map((item) => (
                                    <div key={item.id} className="bg-slate-700 p-3 rounded flex justify-between items-center text-sm">
                                        <div><span className={`font-bold mr-2 ${item.tradeType === 'BUY' ? 'text-green-400' : 'text-red-400'}`}>{item.tradeType}</span><span className="font-bold">{item.symbol}</span> x {item.amount}</div>
                                        <div className="font-mono">${item.price.toFixed(2)}</div>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}