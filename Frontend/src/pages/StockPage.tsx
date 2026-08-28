import { useEffect, useState, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { createChart, ColorType, CandlestickSeries } from 'lightweight-charts';

const POPULAR_STOCKS = [
    'AAPL', 'TSLA', 'MSFT', 'NVDA', 'AMD', 'META', 'AMZN', 'GOOGL', 
    'NFLX', 'INTC', 'TSM', 'BABA', 'V', 'JPM', 'WMT', 'COIN', 'PLTR'
];

interface StockStats { high: number; low: number; open: number; close: number; }

export default function StockPage() {
    const [stocksData, setStocksData] = useState<Record<string, { c: number, d: number, dp: number }>>({});
    const [loading, setLoading] = useState(true);
    
    // ⭐ 즐겨찾기 관련 상태
    const [bookmarks, setBookmarks] = useState<string[]>([]);
    const [showBookmarksOnly, setShowBookmarksOnly] = useState<boolean>(false);

    // 모달창 관련 상태
    const [selectedStock, setSelectedStock] = useState<string | null>(null);
    const [resolution, setResolution] = useState<string>('D');
    const [stats, setStats] = useState<StockStats | null>(null);
    const chartModalContainerRef = useRef<HTMLDivElement>(null);

    // 1. 서버에서 내 즐겨찾기 목록 불러오기
    const fetchBookmarks = async () => {
        const username = localStorage.getItem('username');
        if (!username) return;
        try {
            const res = await fetch(`http://localhost:8080/api/bookmark?username=${username}`);
            if (res.ok) {
                const data = await res.json();
                setBookmarks(data);
            }
        } catch (err) {
            console.error("북마크 조회 실패");
        }
    };

    // 2. 주가 실시간 로딩
    useEffect(() => {
        fetchBookmarks();
        const fetchPrices = async () => {
            const updatedData: Record<string, { c: number, d: number, dp: number }> = {};
            for (const sym of POPULAR_STOCKS) {
                try {
                    const res = await fetch(`http://localhost:8080/api/stock/quote?symbol=${sym}`);
                    const data = await res.json();
                    if (data && data.c) updatedData[sym] = { c: data.c, d: data.d, dp: data.dp };
                } catch (err) {}
            }
            setStocksData(updatedData);
            setLoading(false);
        };
        fetchPrices();
        const interval = setInterval(fetchPrices, 30000); // API 한도 고려 30초
        return () => clearInterval(interval);
    }, []);

    // 3. 즐겨찾기 토글 (별 클릭 시 실행)
    const toggleBookmark = async (symbol: string, currentPrice: number) => {
        const username = localStorage.getItem('username');
        if (!username) return alert("로그인이 필요합니다.");

        try {
            const res = await fetch(`http://localhost:8080/api/bookmark/toggle`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, symbol, price: currentPrice })
            });
            const data = await res.json();
            if (data.status === "ADDED") {
                setBookmarks(prev => [...prev, symbol]);
            } else if (data.status === "REMOVED") {
                setBookmarks(prev => prev.filter(b => b !== symbol));
            }
        } catch (err) {
            alert("북마크 처리에 실패했습니다.");
        }
    };

    // 모달 내 트레이딩뷰 차트 렌더링
    useEffect(() => {
        if (!selectedStock || !chartModalContainerRef.current) return;
        const chart = createChart(chartModalContainerRef.current, {
            layout: { background: { type: ColorType.Solid, color: 'transparent' }, textColor: '#94a3b8' },
            grid: { vertLines: { color: '#334155' }, horzLines: { color: '#334155' } },
            width: chartModalContainerRef.current.clientWidth,
            height: 320,
            localization: { dateFormat: 'yyyy-MM-dd' },
            timeScale: { timeVisible: false, rightOffset: 5 },
        });

        const candlestickSeries = chart.addSeries(CandlestickSeries, {
            upColor: '#22c55e', downColor: '#ef4444', borderVisible: false,
            wickUpColor: '#22c55e', wickDownColor: '#ef4444'
        });

        const fetchCandles = async () => {
            try {
                const res = await fetch(`http://localhost:8080/api/stock/candles?symbol=${selectedStock}&resolution=${resolution}`);
                const data = await res.json();
                if (data.chart && data.chart.result && data.chart.result[0]) {
                    const result = data.chart.result[0];
                    const timestamps = result.timestamp;
                    const quotes = result.indicators.quote[0];
                    if (timestamps && quotes) {
                        const validOpens: number[] = quotes.open.filter((v: any) => v !== null);
                        const validHighs: number[] = quotes.high.filter((v: any) => v !== null);
                        const validLows: number[] = quotes.low.filter((v: any) => v !== null);
                        const validCloses: number[] = quotes.close.filter((v: any) => v !== null);
                        if (validHighs.length > 0) {
                            setStats({
                                high: Math.max(...validHighs), low: Math.min(...validLows),
                                open: validOpens[0], close: validCloses[validCloses.length - 1]
                            });
                        }
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
        const handleResize = () => { if (chartModalContainerRef.current) chart.applyOptions({ width: chartModalContainerRef.current.clientWidth }); };
        window.addEventListener('resize', handleResize);
        return () => { window.removeEventListener('resize', handleResize); chart.remove(); };
    }, [selectedStock, resolution]);

    // 💡 정렬 및 필터링 로직 (변동폭 순 정렬 -> 즐겨찾기 필터 적용)
    let sortedStocks = Object.entries(stocksData).sort(([, a], [, b]) => Math.abs(b.dp) - Math.abs(a.dp));
    if (showBookmarksOnly) {
        sortedStocks = sortedStocks.filter(([sym]) => bookmarks.includes(sym));
    }

    return (
        <div className="min-h-screen bg-slate-900 text-white p-8 font-sans relative">
            <header className="mb-8 border-b border-slate-700 pb-6 flex flex-col md:flex-row md:justify-between md:items-end gap-4">
                <div>
                    <h1 className="text-4xl font-extrabold text-sky-400">📈 Stock & Fund</h1>
                    <p className="text-slate-400 mt-2">원하는 종목을 클릭하여 상세 차트와 데이터 통계를 확인하세요.</p>
                </div>
                
                {/* 🌟 즐겨찾기 토글 스위치 */}
                <div className="flex bg-slate-800 rounded-lg p-1 border border-slate-700">
                    <button onClick={() => setShowBookmarksOnly(false)} className={`px-4 py-2 rounded-md font-bold transition-all ${!showBookmarksOnly ? 'bg-sky-500 text-white' : 'text-slate-400 hover:text-white'}`}>
                        전체 종목
                    </button>
                    <button onClick={() => setShowBookmarksOnly(true)} className={`px-4 py-2 rounded-md font-bold transition-all flex items-center gap-2 ${showBookmarksOnly ? 'bg-sky-500 text-white' : 'text-slate-400 hover:text-white'}`}>
                        <span>⭐</span> 찜한 종목
                    </button>
                </div>
            </header>

            {loading ? (
                <div className="text-center text-slate-400 mt-20 text-xl font-bold animate-pulse">실시간 시장 데이터를 불러오는 중입니다...</div>
            ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6 overflow-y-auto pb-20">
                    {sortedStocks.length === 0 ? (
                        <div className="col-span-full text-center text-slate-500 py-20 text-lg">
                            {showBookmarksOnly ? "즐겨찾기한 종목이 없습니다." : "데이터가 없습니다."}
                        </div>
                    ) : (
                        sortedStocks.map(([sym, data]) => {
                            const isUp = data.d >= 0;
                            const isBookmarked = bookmarks.includes(sym);
                            
                            return (
                                <motion.div 
                                    key={sym} onClick={() => setSelectedStock(sym)}
                                    initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} whileHover={{ scale: 1.03 }}
                                    className="bg-slate-800 border border-slate-700 p-6 rounded-2xl shadow-lg flex flex-col justify-between hover:border-sky-500 cursor-pointer transition-colors"
                                >
                                    <div className="flex justify-between items-start mb-4">
                                        <div className="flex items-center gap-3">
                                            <h2 className="text-2xl font-bold text-white">{sym}</h2>
                                            {/* 🌟 별 모양 즐겨찾기 버튼 (이벤트 버블링 방지) */}
                                            <button 
                                                onClick={(e) => { e.stopPropagation(); toggleBookmark(sym, data.c); }}
                                                className={`text-2xl transition-transform hover:scale-125 ${isBookmarked ? 'text-yellow-400' : 'text-slate-500 hover:text-yellow-200'}`}
                                            >
                                                {isBookmarked ? '⭐' : '☆'}
                                            </button>
                                        </div>
                                        <span className={`px-2 py-1 rounded text-xs font-bold ${isUp ? 'bg-green-500/20 text-green-400' : 'bg-red-500/20 text-red-400'}`}>
                                            {isUp ? '상승' : '하락'}
                                        </span>
                                    </div>
                                    <div>
                                        <div className="text-3xl font-mono font-bold">${data.c.toFixed(2)}</div>
                                        <div className={`text-md font-bold mt-1 ${isUp ? 'text-green-400' : 'text-red-400'}`}>
                                            {isUp ? '▲' : '▼'} {Math.abs(data.d).toFixed(2)} ({isUp ? '+' : ''}{data.dp.toFixed(2)}%)
                                        </div>
                                    </div>
                                    <div className="text-xs text-sky-400 mt-4 font-semibold text-right">차트 분석 ➔</div>
                                </motion.div>
                            );
                        })
                    )}
                </div>
            )}

            {/* 🚀 종목 클릭 시 나타나는 상세 분석 모달 */}
            <AnimatePresence>
                {selectedStock && (
                    <motion.div 
                        initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
                        className="fixed inset-0 bg-black/75 z-50 flex items-center justify-center p-4 backdrop-blur-sm"
                        onClick={() => setSelectedStock(null)}
                    >
                        <motion.div 
                            initial={{ scale: 0.95, y: 20 }} animate={{ scale: 1, y: 0 }} exit={{ scale: 0.95, y: 20 }}
                            className="bg-slate-800 border border-slate-600 rounded-2xl w-full max-w-4xl p-6 shadow-2xl relative"
                            onClick={(e) => e.stopPropagation()}
                        >
                            <div className="flex justify-between items-center mb-4 border-b border-slate-700 pb-3">
                                <div>
                                    <h2 className="text-2xl font-extrabold text-sky-400">{selectedStock} 주가 분석</h2>
                                    <span className="text-xs text-slate-400 font-mono">Real-time Financial Candlestick</span>
                                </div>
                                <div className="flex items-center gap-4">
                                    <div className="flex gap-2 bg-slate-900 p-1 rounded-lg border border-slate-700">
                                        <button onClick={() => setResolution('D')} className={`px-3 py-1 rounded text-xs font-bold ${resolution === 'D' ? 'bg-sky-500 text-white' : 'text-slate-400'}`}>일봉</button>
                                        <button onClick={() => setResolution('W')} className={`px-3 py-1 rounded text-xs font-bold ${resolution === 'W' ? 'bg-sky-500 text-white' : 'text-slate-400'}`}>주봉</button>
                                        <button onClick={() => setResolution('M')} className={`px-3 py-1 rounded text-xs font-bold ${resolution === 'M' ? 'bg-sky-500 text-white' : 'text-slate-400'}`}>월봉</button>
                                    </div>
                                    <button onClick={() => setSelectedStock(null)} className="text-slate-400 hover:text-white text-2xl font-bold">&times;</button>
                                </div>
                            </div>

                            <div className="w-full h-[320px] mb-6" ref={chartModalContainerRef} />

                            {stats && (
                                <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 bg-slate-900 p-4 rounded-xl border border-slate-700">
                                    <div><span className="text-xs text-slate-400">기간 최고가</span><div className="text-lg font-mono font-bold text-green-400">${stats.high.toFixed(2)}</div></div>
                                    <div><span className="text-xs text-slate-400">기간 최저가</span><div className="text-lg font-mono font-bold text-red-400">${stats.low.toFixed(2)}</div></div>
                                    <div><span className="text-xs text-slate-400">기준 시가</span><div className="text-lg font-mono font-bold text-slate-200">${stats.open.toFixed(2)}</div></div>
                                    <div><span className="text-xs text-slate-400">최근 종가</span><div className="text-lg font-mono font-bold text-sky-400">${stats.close.toFixed(2)}</div></div>
                                </div>
                            )}
                        </motion.div>
                    </motion.div>
                )}
            </AnimatePresence>
        </div>
    );
}