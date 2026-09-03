import { API_URL, WS_URL } from '../config';
import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { TrendingUp, TrendingDown, RefreshCw, X, Star } from 'lucide-react';

export default function Watchlist() {
    const [stocks, setStocks] = useState<any[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [selectedStock, setSelectedStock] = useState<any | null>(null);

    const fetchWatchlist = async () => {
        setIsLoading(true);
        const username = localStorage.getItem('username');
        if (!username || username === 'Guest') { 
            setIsLoading(false); 
            return; 
        }
        
        try {
            // DB에서 찜한 종목 리스트 가져오기
            const res = await fetch(`http://localhost:8080/api/bookmark?username=${username}`);
            const bookmarkedSymbols: string[] = await res.json();
            
            if (bookmarkedSymbols.length > 0) {
                // 해당 종목들의 실시간 시세 불러오기
                const promises = bookmarkedSymbols.map(async (symbol) => {
                    const quoteRes = await fetch(`http://localhost:8080/api/stock/quote?symbol=${symbol}`);
                    if (quoteRes.status === 429) return { symbol, error: '한도 초과' };
                    const data = await quoteRes.json();
                    return { symbol, ...data };
                });
                const newStocks = await Promise.all(promises);
                setStocks(newStocks);
            } else {
                setStocks([]);
            }
        } catch (e) {
            console.error(e);
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => { 
        fetchWatchlist(); 
    }, []);

    const removeBookmark = async (e: React.MouseEvent, symbol: string) => {
        e.stopPropagation();
        const username = localStorage.getItem('username');
        if (!username) return;
        try {
            const res = await fetch(`http://localhost:8080/api/bookmark/toggle`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, symbol, price: 0 })
            });
            const data = await res.json();
            if (data.status === 'REMOVED') {
                setStocks(prev => prev.filter(s => s.symbol !== symbol));
            }
        } catch (e) {}
    };

    const isGuest = (localStorage.getItem('username') || 'Guest') === 'Guest';

    return (
        <div className="p-4 sm:p-6 md:p-8 min-h-screen text-slate-200 bg-[#0b1120] relative">
            <div className="mb-8 border-b border-slate-800 pb-4">
                <h1 className="text-3xl font-extrabold text-transparent bg-clip-text bg-gradient-to-r from-sky-400 to-indigo-400 flex items-center gap-3">
                    <Star className="w-8 h-8 text-yellow-400 fill-yellow-400" /> My Watchlist
                </h1>
                <p className="text-slate-400 text-sm mt-2">내가 찜한 관심 종목들을 한눈에 모아보세요.</p>
            </div>

            {isGuest ? (
                <div className="text-center py-20 bg-slate-800/30 rounded-3xl border border-slate-700/50">
                    <Star className="w-16 h-16 text-slate-600 mx-auto mb-4" />
                    <h2 className="text-xl font-bold text-slate-300 mb-2">로그인이 필요합니다</h2>
                    <p className="text-slate-500 text-sm">관심 종목을 등록하고 관리하려면 로그인해 주세요.</p>
                </div>
            ) : isLoading ? (
                <div className="flex flex-col items-center justify-center py-20">
                    <RefreshCw className="w-10 h-10 animate-spin text-sky-500 mb-4" />
                    <span className="text-slate-400 font-bold">관심 종목 시세 불러오는 중...</span>
                </div>
            ) : stocks.length === 0 ? (
                <div className="text-center py-20 bg-slate-800/30 rounded-3xl border border-slate-700/50">
                    <Star className="w-16 h-16 text-slate-600 mx-auto mb-4" />
                    <h2 className="text-xl font-bold text-slate-300 mb-2">등록된 관심 종목이 없습니다</h2>
                    <p className="text-slate-500 text-sm">Stock & Fund 메뉴에서 별표(⭐)를 눌러 추가해 보세요.</p>
                </div>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6 relative z-10">
                    {stocks.map((stock, index) => {
                        const isUp = stock.d > 0;
                        const isDown = stock.d < 0;

                        return (
                            <motion.div key={`${stock.symbol}-${index}`} initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} transition={{ duration: 0.2 }} onClick={() => !stock.error && setSelectedStock(stock)} className={`bg-slate-800/50 backdrop-blur-md p-5 rounded-3xl border border-slate-700/50 shadow-lg transition-all flex flex-col relative overflow-hidden ${stock.error ? 'opacity-70' : 'cursor-pointer hover:border-sky-500 hover:-translate-y-1'}`}>
                                {stock.error ? (
                                    <div className="flex flex-col items-center justify-center h-full text-slate-500 py-6"><RefreshCw className="w-8 h-8 mb-2 opacity-20" /><span className="text-sm font-bold text-rose-500/80">API 호출 대기중...</span></div>
                                ) : (
                                    <>
                                        <div className="flex justify-between items-start mb-4">
                                            <h2 className="text-xl font-black text-white">{stock.symbol}</h2>
                                            <div className="flex items-center gap-3">
                                                {isUp && <TrendingUp className="w-6 h-6 text-emerald-500" />}
                                                {isDown && <TrendingDown className="w-6 h-6 text-rose-500" />}
                                                <button onClick={(e) => removeBookmark(e, stock.symbol)} className="z-10 hover:scale-110 transition-transform">
                                                    <Star className="w-6 h-6 fill-yellow-400 text-yellow-400" />
                                                </button>
                                            </div>
                                        </div>
                                        <div className="mt-auto">
                                            <p className="text-3xl font-mono font-bold tracking-tight text-white">${stock.c?.toFixed(2)}</p>
                                            <div className="flex items-center gap-2 mt-2">
                                                <span className={`text-sm font-bold ${isUp ? 'text-emerald-500' : isDown ? 'text-rose-500' : 'text-slate-400'}`}>{isUp ? '+' : ''}{stock.d?.toFixed(2)}</span>
                                                <span className={`text-sm px-2 py-0.5 rounded-md ${isUp ? 'bg-emerald-500/20 text-emerald-400' : isDown ? 'bg-rose-500/20 text-rose-400' : 'bg-slate-700 text-slate-300'}`}>{isUp ? '+' : ''}{stock.dp?.toFixed(2)}%</span>
                                            </div>
                                        </div>
                                    </>
                                )}
                            </motion.div>
                        );
                    })}
                </div>
            )}

            <AnimatePresence>
                {selectedStock && (
                    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="fixed inset-0 bg-black/80 backdrop-blur-sm z-[100] flex items-center justify-center p-2 sm:p-6" onClick={() => setSelectedStock(null)}>
                        <motion.div initial={{ scale: 0.95, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} exit={{ scale: 0.95, opacity: 0 }} onClick={(e) => e.stopPropagation()} className="bg-slate-900 border border-slate-700 rounded-2xl sm:rounded-3xl w-full max-w-5xl shadow-2xl overflow-hidden flex flex-col md:flex-row h-full max-h-[90vh]">
                            <div className="flex-1 bg-slate-950 p-2 min-h-[300px] md:min-h-[500px]">
                                <iframe src={`https://s.tradingview.com/widgetembed/?symbol=${selectedStock.symbol}&interval=D&theme=dark&style=1&hide_top_toolbar=1&hide_side_toolbar=1&withdateranges=1&saveimage=0&locale=kr`} className="w-full h-full border-0 rounded-xl" allowTransparency={true} />
                            </div>
                            <div className="w-full md:w-80 p-4 sm:p-6 flex flex-col border-t md:border-t-0 md:border-l border-slate-800 overflow-y-auto">
                                <div className="flex justify-between items-start mb-6">
                                    <div><h2 className="text-2xl sm:text-3xl font-black text-white">{selectedStock.symbol}</h2></div>
                                    <button onClick={() => setSelectedStock(null)} className="text-slate-400 hover:text-white bg-slate-800 rounded-full p-1.5 sm:p-1 transition-colors"><X className="w-5 h-5 sm:w-6 sm:h-6" /></button>
                                </div>
                                <div className="mb-6 sm:mb-8">
                                    <p className="text-4xl sm:text-5xl font-mono font-bold text-white">${selectedStock.c?.toFixed(2)}</p>
                                    <div className="flex items-center gap-2 mt-2"><span className={`font-bold ${selectedStock.d > 0 ? 'text-emerald-500' : selectedStock.d < 0 ? 'text-rose-500' : 'text-slate-400'}`}>{selectedStock.d > 0 ? '+' : ''}{selectedStock.d?.toFixed(2)} ({selectedStock.dp > 0 ? '+' : ''}{selectedStock.dp?.toFixed(2)}%)</span></div>
                                </div>
                                <div className="grid grid-cols-2 gap-3 mb-auto">
                                    <div className="bg-slate-800/50 p-3 rounded-xl border border-slate-700/50"><p className="text-xs text-slate-400 mb-1">고가 (High)</p><p className="font-mono font-bold text-white">${selectedStock.h?.toFixed(2)}</p></div>
                                    <div className="bg-slate-800/50 p-3 rounded-xl border border-slate-700/50"><p className="text-xs text-slate-400 mb-1">저가 (Low)</p><p className="font-mono font-bold text-white">${selectedStock.l?.toFixed(2)}</p></div>
                                    <div className="bg-slate-800/50 p-3 rounded-xl border border-slate-700/50"><p className="text-xs text-slate-400 mb-1">시가 (Open)</p><p className="font-mono font-bold text-white">${selectedStock.o?.toFixed(2)}</p></div>
                                    <div className="bg-slate-800/50 p-3 rounded-xl border border-slate-700/50"><p className="text-xs text-slate-400 mb-1">전일 종가 (Prev)</p><p className="font-mono font-bold text-white">${selectedStock.pc?.toFixed(2)}</p></div>
                                </div>
                                <button onClick={() => alert('매수/매도 기능은 추후 연동됩니다.')} className="w-full mt-6 bg-sky-600 hover:bg-sky-500 text-white font-bold py-3 sm:py-4 rounded-xl transition-colors shadow-lg text-base sm:text-lg">거래하기 (Trade)</button>
                            </div>
                        </motion.div>
                    </motion.div>
                )}
            </AnimatePresence>
        </div>
    );
}