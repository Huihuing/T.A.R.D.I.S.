import { API_URL, WS_URL } from '../config';
import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { TrendingUp, TrendingDown, RefreshCw, X, Search, Star, ClipboardList, Trash2 } from 'lucide-react';
import { getAuthHeaders } from '../auth';

interface StockSymbol { symbol: string; description: string; displaySymbol: string; }

const ALL_SYMBOLS = ['AAPL', 'TSLA', 'NVDA', 'MSFT', 'GOOGL', 'AMZN', 'META', 'AMD', 'COIN', 'PLTR', 'TSM', 'V', 'BABA', 'JPM', 'WMT'];
const BATCH_SIZE = 4;

export default function StockPage() {
    const [stocks, setStocks] = useState<any[]>([]);
    const [loadedCount, setLoadedCount] = useState(0);
    const [isLoading, setIsLoading] = useState(false);
    
    const [selectedStock, setSelectedStock] = useState<any | null>(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [isSearching, setIsSearching] = useState(false);
    
    const [allSymbols, setAllSymbols] = useState<StockSymbol[]>([]);
    const [isDropdownOpen, setIsDropdownOpen] = useState(false);

    // 💡 북마크 상태 및 조회 로직
    const [bookmarks, setBookmarks] = useState<string[]>([]);
    const [limitOrders, setLimitOrders] = useState<any[]>([]);
    const [orderSide, setOrderSide] = useState<'BUY' | 'SELL'>('BUY');
    const [orderAmount, setOrderAmount] = useState<number | ''>('');
    const [orderPrice, setOrderPrice] = useState<number | ''>('');
    const [isOrderSaving, setIsOrderSaving] = useState(false);
    const [cancelingOrderId, setCancelingOrderId] = useState<number | null>(null);
    const [orderStatusFilter, setOrderStatusFilter] = useState<
        'ALL' | 'PENDING' | 'FILLED' | 'CANCELLED' | 'REJECTED'
    >('ALL');

    const fetchBookmarks = async () => {
        const username = localStorage.getItem('username');
        if (!username || username === 'Guest') return;
        try {
            const res = await fetch(`${API_URL}/api/bookmark`, { headers: getAuthHeaders(false) });
            if (res.ok) {
                const data = await res.json();
                if (Array.isArray(data)) setBookmarks(data);
            }
        } catch (e) {
            console.error("북마크 조회 실패:", e);
        }
    };

    const fetchStockBatch = async (startIndex: number, endIndex: number) => {
        setIsLoading(true);
        const symbolsToFetch = ALL_SYMBOLS.slice(startIndex, endIndex);
        try {
            const promises = symbolsToFetch.map(async (symbol) => {
                const res = await fetch(`${API_URL}/api/stock/quote?symbol=${symbol}`);
                if (res.status === 429) return { symbol, error: '한도 초과' };
                const data = await res.json();
                return { symbol, ...data };
            });
            const newStocks = await Promise.all(promises);
            setStocks((prev) => {
                const existingSymbols = new Set(prev.map(s => s.symbol));
                const uniqueNew = newStocks.filter(s => !existingSymbols.has(s.symbol));
                return [...prev, ...uniqueNew];
            });
            setLoadedCount(prev =>
                Math.max(
                    prev,
                    Math.min(endIndex, ALL_SYMBOLS.length)
                )
            );
        } catch (error) { console.error(error); } finally { setIsLoading(false); }
    };

    const fetchLimitOrders = async () => {
        const token = localStorage.getItem('token');
        if (!token) {
            setLimitOrders([]);
            return;
        }
        try {
            const res = await fetch(API_URL + '/api/limit-orders', {
                headers: getAuthHeaders(false)
            });
            const data = await res.json().catch(() => []);
            setLimitOrders(res.ok && Array.isArray(data) ? data : []);
        } catch {
            setLimitOrders([]);
        }
    };
    useEffect(() => { 
        fetchBookmarks();
        fetchLimitOrders();
        fetchStockBatch(0, BATCH_SIZE); 
        const fetchAllSymbols = async () => {
            try {
                const res = await fetch(`${API_URL}/api/stock/symbols`);
                const data = await res.json();
                if (Array.isArray(data)) setAllSymbols(data);
            } catch (err) {}
        };
        fetchAllSymbols();
    }, []);

    const handleLoadMore = () => fetchStockBatch(loadedCount, loadedCount + BATCH_SIZE);

    const filteredSymbols = allSymbols.filter(s => s.symbol.toLowerCase().includes(searchQuery.toLowerCase()) || s.description.toLowerCase().includes(searchQuery.toLowerCase())).slice(0, 10);

    const executeSearch = async (targetSymbol: string) => {
        setSearchQuery(''); setIsDropdownOpen(false); setIsSearching(true);
        try {
            const quoteRes = await fetch(`${API_URL}/api/stock/quote?symbol=${targetSymbol}`);
            if (quoteRes.status === 429) return alert("API 호출 한도를 초과했습니다.");
            const quoteData = await quoteRes.json();
            if (quoteData.c === 0 && quoteData.h === 0) alert("시세 데이터를 제공하지 않는 종목입니다.");
            else setSelectedStock({ symbol: targetSymbol, description: allSymbols.find(s => s.symbol === targetSymbol)?.description || '', ...quoteData });
        } catch (error) { alert("검색 중 오류가 발생했습니다."); } finally { setIsSearching(false); }
    };

    const handleFormSearch = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!searchQuery.trim()) return;
        if (filteredSymbols.length > 0) { executeSearch(filteredSymbols[0].symbol); } 
        else {
            setIsSearching(true);
            try {
                const searchRes = await fetch(`${API_URL}/api/stock/search?query=${searchQuery.trim()}`);
                const searchData = await searchRes.json();
                if (searchData.result && searchData.result.length > 0) executeSearch(searchData.result[0].symbol);
                else alert("결과가 없습니다.");
            } catch (err) { alert("서버 오류"); } finally { setIsSearching(false); }
        }
    };

    // 💡 북마크 토글 기능
    const toggleBookmark = async (e: React.MouseEvent, symbol: string, price: number) => {
        e.stopPropagation(); // 카드 클릭(모달 오픈) 방지
        const username = localStorage.getItem('username');
        if (!username || username === 'Guest') return alert("로그인이 필요합니다.");
        
        try {
            const res = await fetch(`${API_URL}/api/bookmark/toggle`, {
                method: 'POST',
                headers: getAuthHeaders(),
                body: JSON.stringify({ symbol, price: price || 0 })
            });
            const data = await res.json();
            if (data.status === 'ADDED') {
                setBookmarks(prev => [...new Set([...prev, symbol])]);
            } else if (data.status === 'REMOVED') {
                setBookmarks(prev => prev.filter(s => s !== symbol));
            }
        } catch (error) {
            console.error("북마크 변경 오류:", error);
        }
    };

    const createLimitOrder = async () => {
        if (!selectedStock?.symbol) return;
        if (!orderAmount || orderAmount <= 0) return alert('주문 수량을 올바르게 입력해주세요.');
        if (!orderPrice || orderPrice <= 0) return alert('지정가를 올바르게 입력해주세요.');
        setIsOrderSaving(true);
        try {
            const res = await fetch(API_URL + '/api/limit-orders', {
                method: 'POST',
                headers: getAuthHeaders(),
                body: JSON.stringify({
                    side: orderSide,
                    symbol: selectedStock.symbol,
                    amount: Number(orderAmount),
                    limitPrice: Number(orderPrice)
                })
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok) return alert(data.message || '지정가 주문 등록에 실패했습니다.');
            alert(selectedStock.symbol + ' ' + Number(orderAmount) + '주 ' + (orderSide === 'BUY' ? '매수' : '매도') + ' 지정가 주문을 등록했습니다.');
            setOrderAmount('');
            setOrderPrice('');
            await fetchLimitOrders();
        } catch {
            alert('지정가 주문 등록 중 오류가 발생했습니다.');
        } finally {
            setIsOrderSaving(false);
        }
    };

    const cancelLimitOrder = async (id: number) => {
        const order = limitOrders.find(item => item.id === id);
        if (!order || order.status !== 'PENDING') return;

        if (!window.confirm(
            `${order.symbol} ${order.amount}주 ${
                order.side === 'BUY' ? '매수' : '매도'
            } 지정가 주문을 취소할까요?`
        )) {
            return;
        }

        setCancelingOrderId(id);
        try {
            const res = await fetch(API_URL + '/api/limit-orders/' + id, {
                method: 'DELETE',
                headers: getAuthHeaders(false)
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                alert(data.message || '주문 취소에 실패했습니다.');
                return;
            }
            await fetchLimitOrders();
        } catch {
            alert('주문 취소 중 오류가 발생했습니다.');
        } finally {
            setCancelingOrderId(null);
        }
    };

    const formatKstDateTime = (value?: string | null) => {
        if (!value) return '';
        const normalized = /[zZ]|[+-]\d{2}:\d{2}$/.test(value)
            ? value
            : `${value}+09:00`;
        const date = new Date(normalized);
        if (Number.isNaN(date.getTime())) return value;
        return new Intl.DateTimeFormat('ko-KR', {
            timeZone: 'Asia/Seoul',
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            hour12: false
        }).format(date);
    };

    const orderStatusLabel = (status: string) => ({
        PENDING: '대기',
        FILLED: '체결',
        CANCELLED: '취소',
        REJECTED: '거절'
    }[status] || status);

    const orderStatusClass = (status: string) => {
        if (status === 'PENDING') {
            return 'bg-amber-500/15 text-amber-300 border-amber-500/20';
        }
        if (status === 'FILLED') {
            return 'bg-emerald-500/15 text-emerald-300 border-emerald-500/20';
        }
        if (status === 'REJECTED') {
            return 'bg-rose-500/15 text-rose-300 border-rose-500/20';
        }
        return 'bg-slate-700/70 text-slate-300 border-slate-600';
    };

    const filteredLimitOrders =
        orderStatusFilter === 'ALL'
            ? limitOrders
            : limitOrders.filter(
                order => order.status === orderStatusFilter
            );

    const estimatedOrderValue =
        orderAmount && orderPrice
            ? Number(orderAmount) * Number(orderPrice)
            : 0;

    const likelyImmediateTrigger =
        Boolean(
            selectedStock?.c
            && orderPrice
            && (
                (orderSide === 'BUY'
                    && Number(selectedStock.c) <= Number(orderPrice))
                || (orderSide === 'SELL'
                    && Number(selectedStock.c) >= Number(orderPrice))
            )
        );
    return (
        <div className="p-4 sm:p-6 md:p-8 min-h-screen text-slate-200 bg-[#0b1120] relative">
            <div className="flex flex-col md:flex-row justify-between items-start md:items-center mb-8 border-b border-slate-800 pb-4 gap-4">
                <div>
                    <h1 className="text-3xl font-extrabold text-transparent bg-clip-text bg-gradient-to-r from-sky-400 to-indigo-400">Global Market</h1>
                    <p className="text-slate-400 text-sm mt-1">실시간 글로벌 주식 시세 및 검색</p>
                </div>
                <form onSubmit={handleFormSearch} className="flex gap-2 w-full md:w-auto relative z-[60]">
                    <div className="relative w-full md:w-72">
                        <input type="text" value={searchQuery} onChange={(e) => { setSearchQuery(e.target.value); setIsDropdownOpen(true); }} onFocus={() => setIsDropdownOpen(true)} placeholder="회사명 또는 티커 검색..." className="bg-slate-800/80 border border-slate-700 focus:border-sky-500 rounded-xl px-4 py-2.5 outline-none text-sm w-full transition-colors uppercase shadow-inner" />
                        {isDropdownOpen && searchQuery && (
                            <div className="absolute top-14 left-0 right-0 bg-slate-800 border border-slate-600 rounded-xl shadow-2xl z-[70] max-h-60 overflow-y-auto">
                                {filteredSymbols.map((item) => (
                                    <div key={item.symbol} onClick={() => executeSearch(item.symbol)} className="px-4 py-3 hover:bg-slate-700 cursor-pointer flex justify-between items-center border-b border-slate-700/50">
                                        <span className="font-bold text-sky-400">{item.symbol}</span>
                                        <span className="text-xs text-slate-300 truncate max-w-[150px]">{item.description}</span>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                    <button type="submit" disabled={isSearching} className="bg-sky-600 hover:bg-sky-500 text-white px-4 py-2 rounded-xl font-bold flex items-center justify-center transition-colors shadow-lg disabled:opacity-50 shrink-0">
                        {isSearching ? <RefreshCw className="w-5 h-5 animate-spin" /> : <Search className="w-5 h-5" />}
                    </button>
                </form>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6 relative z-10">
                {stocks.map((stock, index) => {
                    const isUp = stock.d > 0;
                    const isDown = stock.d < 0;
                    const isBookmarked = bookmarks.includes(stock.symbol);

                    return (
                        <motion.div key={`${stock.symbol}-${index}`} initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3, delay: (index % BATCH_SIZE) * 0.1 }} onClick={() => !stock.error && setSelectedStock(stock)} className={`bg-slate-800/50 backdrop-blur-md p-5 rounded-3xl border border-slate-700/50 shadow-lg transition-all flex flex-col relative overflow-hidden ${stock.error ? 'opacity-70' : 'cursor-pointer hover:border-sky-500 hover:-translate-y-1'}`}>
                            {stock.error ? (
                                <div className="flex flex-col items-center justify-center h-full text-slate-500 py-6"><RefreshCw className="w-8 h-8 mb-2 opacity-20" /><span className="text-sm font-bold text-rose-500/80">API 호출 대기중...</span></div>
                            ) : (
                                <>
                                    <div className="flex justify-between items-start mb-4">
                                        <h2 className="text-xl font-black text-white">{stock.symbol}</h2>
                                        <div className="flex items-center gap-3">
                                            {isUp && <TrendingUp className="w-6 h-6 text-emerald-500" />}
                                            {isDown && <TrendingDown className="w-6 h-6 text-rose-500" />}
                                            {/* 💡 관심 종목 버튼 */}
                                            <button onClick={(e) => toggleBookmark(e, stock.symbol, stock.c)} className="z-10 hover:scale-110 transition-transform">
                                                <Star className={`w-6 h-6 ${isBookmarked ? 'fill-yellow-400 text-yellow-400' : 'text-slate-500'}`} />
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

            {loadedCount < ALL_SYMBOLS.length && (
                <div className="mt-12 text-center relative z-10">
                    <button onClick={handleLoadMore} disabled={isLoading} className="bg-slate-800 hover:bg-slate-700 border border-slate-600 text-sky-400 px-8 py-3 rounded-xl font-bold transition-all shadow-lg disabled:opacity-50 flex items-center justify-center gap-2 mx-auto">
                        {isLoading ? <><RefreshCw className="w-5 h-5 animate-spin" /> <span>불러오는 중...</span></> : <span>▼ {BATCH_SIZE}개 더 보기 ({loadedCount} / {ALL_SYMBOLS.length})</span>}
                    </button>
                </div>
            )}

            {localStorage.getItem("token") && (
                <div className="mt-8 bg-slate-800/40 border border-slate-700/50 rounded-3xl p-5">
                    <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4 mb-4">
                        <div className="flex items-center gap-2">
                            <ClipboardList className="w-5 h-5 text-sky-400" />
                            <h2 className="font-extrabold text-white">지정가 주문 내역</h2>
                            <span className="text-xs text-slate-500">
                                대기 {limitOrders.filter(order => order.status === 'PENDING').length}건
                            </span>
                            <button
                                type="button"
                                onClick={fetchLimitOrders}
                                className="p-1.5 text-slate-500 hover:text-sky-300 rounded-lg hover:bg-slate-800"
                                title="주문 내역 새로고침"
                            >
                                <RefreshCw className="w-4 h-4" />
                            </button>
                        </div>

                        <div className="flex flex-wrap gap-1.5">
                            {[
                                ['ALL', '전체'],
                                ['PENDING', '대기'],
                                ['FILLED', '체결'],
                                ['CANCELLED', '취소'],
                                ['REJECTED', '거절']
                            ].map(([value, label]) => (
                                <button
                                    key={value}
                                    type="button"
                                    onClick={() =>
                                        setOrderStatusFilter(
                                            value as typeof orderStatusFilter
                                        )
                                    }
                                    className={
                                        'px-3 py-1.5 rounded-lg text-xs font-bold border transition-colors '
                                        + (orderStatusFilter === value
                                            ? 'bg-sky-500/15 text-sky-300 border-sky-500/30'
                                            : 'bg-slate-900/50 text-slate-500 border-slate-700 hover:text-slate-300')
                                    }
                                >
                                    {label}
                                    <span className="ml-1 opacity-60">
                                        {value === 'ALL'
                                            ? limitOrders.length
                                            : limitOrders.filter(
                                                order => order.status === value
                                            ).length}
                                    </span>
                                </button>
                            ))}
                        </div>
                    </div>

                    {limitOrders.length === 0 ? (
                        <p className="text-sm text-slate-500">아직 등록된 지정가 주문이 없습니다.</p>
                    ) : filteredLimitOrders.length === 0 ? (
                        <p className="text-sm text-slate-500 py-4 text-center">
                            이 상태의 주문이 없습니다.
                        </p>
                    ) : (
                        <div className="flex flex-col gap-2">
                            {filteredLimitOrders.map(order => (
                                <div
                                    key={order.id}
                                    className="grid grid-cols-1 lg:grid-cols-[1fr_auto_auto] gap-3 items-center bg-slate-900/50 border border-slate-700/60 rounded-xl px-4 py-3"
                                >
                                    <div className="min-w-0">
                                        <div className="flex flex-wrap items-center gap-2">
                                            <p className="font-bold text-white">
                                                {order.symbol} · {order.amount}주 · {order.side === 'BUY' ? '매수' : '매도'}
                                            </p>
                                            <span
                                                className={
                                                    'text-[11px] font-bold px-2 py-1 rounded-lg border '
                                                    + orderStatusClass(order.status)
                                                }
                                            >
                                                {orderStatusLabel(order.status)}
                                            </span>
                                        </div>
                                        <p className="text-xs text-slate-400 mt-1">
                                            지정가 ${Number(order.limitPrice).toFixed(2)}
                                            {order.fillPrice
                                                ? ' · 체결가 
            <AnimatePresence>
                {selectedStock && (
                    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="fixed inset-0 bg-black/80 backdrop-blur-sm z-[100] flex items-center justify-center p-2 sm:p-6" onClick={() => setSelectedStock(null)}>
                        <motion.div initial={{ scale: 0.95, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} exit={{ scale: 0.95, opacity: 0 }} onClick={(e) => e.stopPropagation()} className="bg-slate-900 border border-slate-700 rounded-2xl sm:rounded-3xl w-full max-w-5xl shadow-2xl overflow-hidden flex flex-col md:flex-row h-full max-h-[90vh]">
                            <div className="flex-1 bg-slate-950 p-2 min-h-[300px] md:min-h-[500px]">
                                <iframe src={`https://s.tradingview.com/widgetembed/?symbol=${selectedStock.symbol}&interval=D&theme=dark&style=1&hide_top_toolbar=1&hide_side_toolbar=1&withdateranges=1&saveimage=0&locale=kr`} className="w-full h-full border-0 rounded-xl" allowTransparency={true} />
                            </div>
                            <div className="w-full md:w-80 p-4 sm:p-6 flex flex-col border-t md:border-t-0 md:border-l border-slate-800 overflow-y-auto">
                                <div className="flex justify-between items-start mb-6">
                                    <div>
                                        <div className="flex items-center gap-3">
                                            <h2 className="text-2xl sm:text-3xl font-black text-white">{selectedStock.symbol}</h2>
                                            <button
                                                onClick={(e) => toggleBookmark(e, selectedStock.symbol, selectedStock.c || 0)}
                                                className="p-1 rounded-lg hover:bg-slate-800 transition-all hover:scale-110"
                                                title="관심 종목 (Watchlist) 추가/삭제"
                                            >
                                                <Star className={`w-6 h-6 ${bookmarks.includes(selectedStock.symbol) ? 'fill-yellow-400 text-yellow-400' : 'text-slate-500'}`} />
                                            </button>
                                        </div>
                                        {selectedStock.description && <p className="text-xs text-slate-400 mt-1 truncate w-[200px] md:w-48">{selectedStock.description}</p>}
                                    </div>
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
                                <div className="mt-6 pt-5 border-t border-slate-800">
                                    <div className="flex items-center gap-2 mb-3">
                                        <ClipboardList className="w-4 h-4 text-sky-400" />
                                        <h3 className="font-bold text-white text-sm">지정가 주문</h3>
                                    </div>
                                    <div className="grid grid-cols-2 gap-2 mb-2">
                                        <button type="button" onClick={() => setOrderSide('BUY')} className={'py-2 rounded-lg font-bold text-sm ' + (orderSide === 'BUY' ? 'bg-emerald-600 text-white' : 'bg-slate-800 text-slate-400')}>지정가 매수</button>
                                        <button type="button" onClick={() => setOrderSide('SELL')} className={'py-2 rounded-lg font-bold text-sm ' + (orderSide === 'SELL' ? 'bg-rose-600 text-white' : 'bg-slate-800 text-slate-400')}>지정가 매도</button>
                                    </div>
                                    <div className="grid grid-cols-2 gap-2">
                                        <input type="number" min="1" step="1" value={orderAmount} onChange={e => setOrderAmount(e.target.value === "" ? "" : Number(e.target.value))} placeholder="수량" className="bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-white outline-none" />
                                        <input type="number" min="0.01" step="0.01" value={orderPrice} onChange={e => setOrderPrice(e.target.value === "" ? "" : Number(e.target.value))} placeholder="지정가" className="bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-white outline-none" />
                                    </div>
                                    <div className="mt-3 bg-slate-800/70 border border-slate-700 rounded-xl p-3 text-xs">
                                        <div className="flex justify-between text-slate-400">
                                            <span>예상 주문금액</span>
                                            <span className="font-mono font-bold text-slate-200">
                                                ${estimatedOrderValue > 0
                                                    ? '
                                </div>
                            </div>
                        </motion.div>
                    </motion.div>
                )}
            </AnimatePresence>
        </div>
    );
} + Number(order.fillPrice).toFixed(2)
                                                : ''}
                                        </p>
                                        <div className="text-[11px] text-slate-600 mt-1.5 flex flex-wrap gap-x-3 gap-y-1">
                                            <span>등록 {formatKstDateTime(order.createdAt)}</span>
                                            {order.completedAt && (
                                                <span>완료 {formatKstDateTime(order.completedAt)}</span>
                                            )}
                                        </div>
                                        {order.resultMessage && order.status !== 'PENDING' && (
                                            <p className={
                                                'text-xs mt-2 '
                                                + (order.status === 'REJECTED'
                                                    ? 'text-rose-300'
                                                    : 'text-slate-500')
                                            }>
                                                {order.resultMessage}
                                            </p>
                                        )}
                                    </div>

                                    <div className="text-xs text-slate-500 lg:text-right">
                                        주문 #{order.id}
                                    </div>

                                    {order.status === 'PENDING' ? (
                                        <button
                                            type="button"
                                            disabled={cancelingOrderId === order.id}
                                            onClick={() => cancelLimitOrder(order.id)}
                                            className="inline-flex items-center justify-center gap-1.5 px-3 py-2 text-rose-300 hover:bg-rose-500/10 rounded-lg font-bold text-xs disabled:opacity-50"
                                            aria-label="지정가 주문 취소"
                                        >
                                            {cancelingOrderId === order.id ? (
                                                <RefreshCw className="w-4 h-4 animate-spin" />
                                            ) : (
                                                <Trash2 className="w-4 h-4" />
                                            )}
                                            취소
                                        </button>
                                    ) : (
                                        <span className="hidden lg:block w-[70px]" />
                                    )}
                                </div>
                            ))}
                        </div>
                    )}
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
                                    <div>
                                        <div className="flex items-center gap-3">
                                            <h2 className="text-2xl sm:text-3xl font-black text-white">{selectedStock.symbol}</h2>
                                            <button
                                                onClick={(e) => toggleBookmark(e, selectedStock.symbol, selectedStock.c || 0)}
                                                className="p-1 rounded-lg hover:bg-slate-800 transition-all hover:scale-110"
                                                title="관심 종목 (Watchlist) 추가/삭제"
                                            >
                                                <Star className={`w-6 h-6 ${bookmarks.includes(selectedStock.symbol) ? 'fill-yellow-400 text-yellow-400' : 'text-slate-500'}`} />
                                            </button>
                                        </div>
                                        {selectedStock.description && <p className="text-xs text-slate-400 mt-1 truncate w-[200px] md:w-48">{selectedStock.description}</p>}
                                    </div>
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
                                <div className="mt-6 pt-5 border-t border-slate-800">
                                    <div className="flex items-center gap-2 mb-3">
                                        <ClipboardList className="w-4 h-4 text-sky-400" />
                                        <h3 className="font-bold text-white text-sm">지정가 주문</h3>
                                    </div>
                                    <div className="grid grid-cols-2 gap-2 mb-2">
                                        <button type="button" onClick={() => setOrderSide('BUY')} className={'py-2 rounded-lg font-bold text-sm ' + (orderSide === 'BUY' ? 'bg-emerald-600 text-white' : 'bg-slate-800 text-slate-400')}>지정가 매수</button>
                                        <button type="button" onClick={() => setOrderSide('SELL')} className={'py-2 rounded-lg font-bold text-sm ' + (orderSide === 'SELL' ? 'bg-rose-600 text-white' : 'bg-slate-800 text-slate-400')}>지정가 매도</button>
                                    </div>
                                    <div className="grid grid-cols-2 gap-2">
                                        <input type="number" min="1" step="1" value={orderAmount} onChange={e => setOrderAmount(e.target.value === "" ? "" : Number(e.target.value))} placeholder="수량" className="bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-white outline-none" />
                                        <input type="number" min="0.01" step="0.01" value={orderPrice} onChange={e => setOrderPrice(e.target.value === "" ? "" : Number(e.target.value))} placeholder="지정가" className="bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-white outline-none" />
                                    </div>
                                    <button type="button" onClick={createLimitOrder} disabled={isOrderSaving} className="w-full mt-2 bg-sky-600 hover:bg-sky-500 text-white font-bold py-2.5 rounded-lg disabled:opacity-50">
                                        {isOrderSaving ? '주문 등록 중...' : '지정가 주문 등록'}
                                    </button>
                                    <p className="text-[11px] text-slate-500 mt-2 leading-relaxed">매수는 현재가가 지정가 이하, 매도는 현재가가 지정가 이상일 때 체결됩니다.</p>
                                </div>
                            </div>
                        </motion.div>
                    </motion.div>
                )}
            </AnimatePresence>
        </div>
    );
} + estimatedOrderValue.toFixed(2)
                                                    : '-'}
                                            </span>
                                        </div>
                                        <div className="flex justify-between text-slate-500 mt-1">
                                            <span>현재가</span>
                                            <span className="font-mono">
                                                ${selectedStock.c
                                                    ? '
                                </div>
                            </div>
                        </motion.div>
                    </motion.div>
                )}
            </AnimatePresence>
        </div>
    );
} + Number(order.fillPrice).toFixed(2)
                                                : ''}
                                        </p>
                                        <div className="text-[11px] text-slate-600 mt-1.5 flex flex-wrap gap-x-3 gap-y-1">
                                            <span>등록 {formatKstDateTime(order.createdAt)}</span>
                                            {order.completedAt && (
                                                <span>완료 {formatKstDateTime(order.completedAt)}</span>
                                            )}
                                        </div>
                                        {order.resultMessage && order.status !== 'PENDING' && (
                                            <p className={
                                                'text-xs mt-2 '
                                                + (order.status === 'REJECTED'
                                                    ? 'text-rose-300'
                                                    : 'text-slate-500')
                                            }>
                                                {order.resultMessage}
                                            </p>
                                        )}
                                    </div>

                                    <div className="text-xs text-slate-500 lg:text-right">
                                        주문 #{order.id}
                                    </div>

                                    {order.status === 'PENDING' ? (
                                        <button
                                            type="button"
                                            disabled={cancelingOrderId === order.id}
                                            onClick={() => cancelLimitOrder(order.id)}
                                            className="inline-flex items-center justify-center gap-1.5 px-3 py-2 text-rose-300 hover:bg-rose-500/10 rounded-lg font-bold text-xs disabled:opacity-50"
                                            aria-label="지정가 주문 취소"
                                        >
                                            {cancelingOrderId === order.id ? (
                                                <RefreshCw className="w-4 h-4 animate-spin" />
                                            ) : (
                                                <Trash2 className="w-4 h-4" />
                                            )}
                                            취소
                                        </button>
                                    ) : (
                                        <span className="hidden lg:block w-[70px]" />
                                    )}
                                </div>
                            ))}
                        </div>
                    )}
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
                                    <div>
                                        <div className="flex items-center gap-3">
                                            <h2 className="text-2xl sm:text-3xl font-black text-white">{selectedStock.symbol}</h2>
                                            <button
                                                onClick={(e) => toggleBookmark(e, selectedStock.symbol, selectedStock.c || 0)}
                                                className="p-1 rounded-lg hover:bg-slate-800 transition-all hover:scale-110"
                                                title="관심 종목 (Watchlist) 추가/삭제"
                                            >
                                                <Star className={`w-6 h-6 ${bookmarks.includes(selectedStock.symbol) ? 'fill-yellow-400 text-yellow-400' : 'text-slate-500'}`} />
                                            </button>
                                        </div>
                                        {selectedStock.description && <p className="text-xs text-slate-400 mt-1 truncate w-[200px] md:w-48">{selectedStock.description}</p>}
                                    </div>
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
                                <div className="mt-6 pt-5 border-t border-slate-800">
                                    <div className="flex items-center gap-2 mb-3">
                                        <ClipboardList className="w-4 h-4 text-sky-400" />
                                        <h3 className="font-bold text-white text-sm">지정가 주문</h3>
                                    </div>
                                    <div className="grid grid-cols-2 gap-2 mb-2">
                                        <button type="button" onClick={() => setOrderSide('BUY')} className={'py-2 rounded-lg font-bold text-sm ' + (orderSide === 'BUY' ? 'bg-emerald-600 text-white' : 'bg-slate-800 text-slate-400')}>지정가 매수</button>
                                        <button type="button" onClick={() => setOrderSide('SELL')} className={'py-2 rounded-lg font-bold text-sm ' + (orderSide === 'SELL' ? 'bg-rose-600 text-white' : 'bg-slate-800 text-slate-400')}>지정가 매도</button>
                                    </div>
                                    <div className="grid grid-cols-2 gap-2">
                                        <input type="number" min="1" step="1" value={orderAmount} onChange={e => setOrderAmount(e.target.value === "" ? "" : Number(e.target.value))} placeholder="수량" className="bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-white outline-none" />
                                        <input type="number" min="0.01" step="0.01" value={orderPrice} onChange={e => setOrderPrice(e.target.value === "" ? "" : Number(e.target.value))} placeholder="지정가" className="bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-white outline-none" />
                                    </div>
                                    <button type="button" onClick={createLimitOrder} disabled={isOrderSaving} className="w-full mt-2 bg-sky-600 hover:bg-sky-500 text-white font-bold py-2.5 rounded-lg disabled:opacity-50">
                                        {isOrderSaving ? '주문 등록 중...' : '지정가 주문 등록'}
                                    </button>
                                    <p className="text-[11px] text-slate-500 mt-2 leading-relaxed">매수는 현재가가 지정가 이하, 매도는 현재가가 지정가 이상일 때 체결됩니다.</p>
                                </div>
                            </div>
                        </motion.div>
                    </motion.div>
                )}
            </AnimatePresence>
        </div>
    );
} + Number(selectedStock.c).toFixed(2)
                                                    : '-'}
                                            </span>
                                        </div>
                                        {likelyImmediateTrigger && (
                                            <p className="mt-2 text-amber-300 leading-relaxed">
                                                현재가가 이미 체결 조건을 만족합니다. 다음 주문 처리 시 빠르게 체결될 수 있습니다.
                                            </p>
                                        )}
                                    </div>
                                    <button type="button" onClick={createLimitOrder} disabled={isOrderSaving} className="w-full mt-2 bg-sky-600 hover:bg-sky-500 text-white font-bold py-2.5 rounded-lg disabled:opacity-50">
                                        {isOrderSaving ? '주문 등록 중...' : '지정가 주문 등록'}
                                    </button>
                                    <p className="text-[11px] text-slate-500 mt-2 leading-relaxed">매수는 현재가가 지정가 이하, 매도는 현재가가 지정가 이상일 때 체결됩니다. 주문은 체결 시점의 잔액·보유 수량을 다시 확인합니다.</p>
                                </div>
                            </div>
                        </motion.div>
                    </motion.div>
                )}
            </AnimatePresence>
        </div>
    );
} + Number(order.fillPrice).toFixed(2)
                                                : ''}
                                        </p>
                                        <div className="text-[11px] text-slate-600 mt-1.5 flex flex-wrap gap-x-3 gap-y-1">
                                            <span>등록 {formatKstDateTime(order.createdAt)}</span>
                                            {order.completedAt && (
                                                <span>완료 {formatKstDateTime(order.completedAt)}</span>
                                            )}
                                        </div>
                                        {order.resultMessage && order.status !== 'PENDING' && (
                                            <p className={
                                                'text-xs mt-2 '
                                                + (order.status === 'REJECTED'
                                                    ? 'text-rose-300'
                                                    : 'text-slate-500')
                                            }>
                                                {order.resultMessage}
                                            </p>
                                        )}
                                    </div>

                                    <div className="text-xs text-slate-500 lg:text-right">
                                        주문 #{order.id}
                                    </div>

                                    {order.status === 'PENDING' ? (
                                        <button
                                            type="button"
                                            disabled={cancelingOrderId === order.id}
                                            onClick={() => cancelLimitOrder(order.id)}
                                            className="inline-flex items-center justify-center gap-1.5 px-3 py-2 text-rose-300 hover:bg-rose-500/10 rounded-lg font-bold text-xs disabled:opacity-50"
                                            aria-label="지정가 주문 취소"
                                        >
                                            {cancelingOrderId === order.id ? (
                                                <RefreshCw className="w-4 h-4 animate-spin" />
                                            ) : (
                                                <Trash2 className="w-4 h-4" />
                                            )}
                                            취소
                                        </button>
                                    ) : (
                                        <span className="hidden lg:block w-[70px]" />
                                    )}
                                </div>
                            ))}
                        </div>
                    )}
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
                                    <div>
                                        <div className="flex items-center gap-3">
                                            <h2 className="text-2xl sm:text-3xl font-black text-white">{selectedStock.symbol}</h2>
                                            <button
                                                onClick={(e) => toggleBookmark(e, selectedStock.symbol, selectedStock.c || 0)}
                                                className="p-1 rounded-lg hover:bg-slate-800 transition-all hover:scale-110"
                                                title="관심 종목 (Watchlist) 추가/삭제"
                                            >
                                                <Star className={`w-6 h-6 ${bookmarks.includes(selectedStock.symbol) ? 'fill-yellow-400 text-yellow-400' : 'text-slate-500'}`} />
                                            </button>
                                        </div>
                                        {selectedStock.description && <p className="text-xs text-slate-400 mt-1 truncate w-[200px] md:w-48">{selectedStock.description}</p>}
                                    </div>
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
                                <div className="mt-6 pt-5 border-t border-slate-800">
                                    <div className="flex items-center gap-2 mb-3">
                                        <ClipboardList className="w-4 h-4 text-sky-400" />
                                        <h3 className="font-bold text-white text-sm">지정가 주문</h3>
                                    </div>
                                    <div className="grid grid-cols-2 gap-2 mb-2">
                                        <button type="button" onClick={() => setOrderSide('BUY')} className={'py-2 rounded-lg font-bold text-sm ' + (orderSide === 'BUY' ? 'bg-emerald-600 text-white' : 'bg-slate-800 text-slate-400')}>지정가 매수</button>
                                        <button type="button" onClick={() => setOrderSide('SELL')} className={'py-2 rounded-lg font-bold text-sm ' + (orderSide === 'SELL' ? 'bg-rose-600 text-white' : 'bg-slate-800 text-slate-400')}>지정가 매도</button>
                                    </div>
                                    <div className="grid grid-cols-2 gap-2">
                                        <input type="number" min="1" step="1" value={orderAmount} onChange={e => setOrderAmount(e.target.value === "" ? "" : Number(e.target.value))} placeholder="수량" className="bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-white outline-none" />
                                        <input type="number" min="0.01" step="0.01" value={orderPrice} onChange={e => setOrderPrice(e.target.value === "" ? "" : Number(e.target.value))} placeholder="지정가" className="bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-white outline-none" />
                                    </div>
                                    <button type="button" onClick={createLimitOrder} disabled={isOrderSaving} className="w-full mt-2 bg-sky-600 hover:bg-sky-500 text-white font-bold py-2.5 rounded-lg disabled:opacity-50">
                                        {isOrderSaving ? '주문 등록 중...' : '지정가 주문 등록'}
                                    </button>
                                    <p className="text-[11px] text-slate-500 mt-2 leading-relaxed">매수는 현재가가 지정가 이하, 매도는 현재가가 지정가 이상일 때 체결됩니다.</p>
                                </div>
                            </div>
                        </motion.div>
                    </motion.div>
                )}
            </AnimatePresence>
        </div>
    );
}