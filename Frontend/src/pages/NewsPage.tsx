import { API_URL, WS_URL } from '../config';
import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { Search, RefreshCw, RotateCcw } from 'lucide-react';

interface NewsItem { id: number; headline: string; summary: string; url: string; image: string; datetime: number; }
interface NaverNewsItem { title: string; link: string; description: string; pubDate: string; }

export default function NewsPage() {
    const [globalNews, setGlobalNews] = useState<NewsItem[]>([]);
    const [koreaNews, setKoreaNews] = useState<NaverNewsItem[]>([]);
    const [newsTab, setNewsTab] = useState<'global' | 'korea'>('global');
    const [loading, setLoading] = useState(true);
    
    const [searchQuery, setSearchQuery] = useState('');
    const defaultGlobal = 'AAPL';
    const defaultKorea = '증시 특징주';
    
    const [currentGlobalQuery, setCurrentGlobalQuery] = useState(defaultGlobal); 
    const [currentKoreaQuery, setCurrentKoreaQuery] = useState(defaultKorea); 

    const fetchNews = async (globalSymbol: string, koreaKeyword: string) => {
        setLoading(true);
        try {
            const resGlobal = await fetch(`${API_URL}/api/news/global?symbol=${globalSymbol}`);
            const globalData = await resGlobal.json();
            if (Array.isArray(globalData)) setGlobalNews(globalData);
            
            const resKorea = await fetch(`${API_URL}/api/news/korea?query=${koreaKeyword}`);
            const koreaData = await resKorea.json();
            if (koreaData && koreaData.items) setKoreaNews(koreaData.items);
        } catch (err) { console.error("뉴스 로딩 에러:", err); }
        setLoading(false);
    };

    useEffect(() => {
        fetchNews(currentGlobalQuery, currentKoreaQuery);
    }, [currentGlobalQuery, currentKoreaQuery]);

    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        if (!searchQuery.trim()) return;
        const query = searchQuery.trim().toUpperCase();
        setCurrentGlobalQuery(query);
        setCurrentKoreaQuery(query + ' 주식'); 
    };

    const handleReset = () => {
        setSearchQuery('');
        setCurrentGlobalQuery(defaultGlobal);
        setCurrentKoreaQuery(defaultKorea);
    };

    // 💡 URL 출처 기반 썸네일 매핑 로직
    const getThumbnail = (url: string, originalImage?: string) => {
        const lowerUrl = url.toLowerCase();
        
        // 1. 해외 언론사 로고
        if (lowerUrl.includes('yahoo.com')) return 'https://s.yimg.com/rz/p/yahoo_frontpage_en-US_s_f_p_bestfit_frontpage_2x.png';
        if (lowerUrl.includes('reuters.com')) return 'https://www.reuters.com/pf/resources/images/reuters/logo-vertical-default.svg?d=169';
        if (lowerUrl.includes('bloomberg.com')) return 'https://assets.bwbx.io/s3/javelin/public/hub/images/bloomberg-logo-01-f10d0f507b.png';
        if (lowerUrl.includes('cnbc.com')) return 'https://searchlogovector.com/wp-content/uploads/2018/06/cnbc-logo-vector.png';
        
        // 2. 국내 언론사 로고 (네이버 뉴스는 출처로 직접 이동하는 경우가 많음)
        if (lowerUrl.includes('hankyung.com')) return 'https://static.hankyung.com/resource/www/img/logo/logo_hk_p.png';
        if (lowerUrl.includes('mk.co.kr')) return 'https://file.mk.co.kr/mklab/design/logo_mk.png';
        if (lowerUrl.includes('mt.co.kr')) return 'https://image.mt.co.kr/renew/logo_mt.png';
        if (lowerUrl.includes('naver.com')) return 'https://s.pstatic.net/static/www/mobile/edit/2016/0705/mobile_212852414260.png';

        // 3. 매핑되지 않은 경우, 기존 이미지가 정상적이면 그대로 사용 (야후 제외)
        if (originalImage && originalImage.trim() !== '' && !originalImage.includes('yahoo')) {
            return originalImage;
        }

        // 4. 아예 이미지가 없는 국내 기사나 썸네일이 없는 기사를 위한 고급 금융 배경 이미지
        return 'https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?q=80&w=1000&auto=format&fit=crop';
    };

    return (
        <div className="min-h-screen bg-[#0b1120] text-slate-200 p-6 md:p-8 font-sans">
            <header className="mb-8 border-b border-slate-800 pb-6 flex flex-col gap-4">
                <div className="flex flex-col md:flex-row justify-between items-start md:items-end gap-4 w-full">
                    <div>
                        <h1 className="text-3xl font-extrabold text-transparent bg-clip-text bg-gradient-to-r from-sky-400 to-indigo-400">
                            📰 Market News
                        </h1>
                        <p className="text-slate-400 text-sm mt-1">국내외 시장의 최신 소식을 확인하세요.</p>
                    </div>
                    <div className="flex bg-slate-800 rounded-lg p-1 border border-slate-700 shadow-md">
                        <button onClick={() => setNewsTab('global')} className={`px-6 py-1.5 rounded-md font-bold text-sm transition-all ${newsTab === 'global' ? 'bg-sky-500 text-white' : 'text-slate-400 hover:text-white'}`}>해외 증시</button>
                        <button onClick={() => setNewsTab('korea')} className={`px-6 py-1.5 rounded-md font-bold text-sm transition-all ${newsTab === 'korea' ? 'bg-sky-500 text-white' : 'text-slate-400 hover:text-white'}`}>국내 증시</button>
                    </div>
                </div>

                <form onSubmit={handleSearch} className="flex gap-2 w-full md:w-[26rem] relative mt-2">
                    <input 
                        type="text" 
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        placeholder="종목명 검색 (예: TSLA, 삼성전자)" 
                        className="bg-slate-800/80 border border-slate-700 focus:border-sky-500 rounded-xl px-4 py-2.5 outline-none text-sm w-full transition-colors shadow-inner text-white"
                    />
                    <button type="submit" disabled={loading} className="bg-slate-700 hover:bg-sky-600 text-white px-4 py-2 rounded-xl font-bold flex items-center justify-center transition-colors shadow-lg disabled:opacity-50 shrink-0" title="검색">
                        {loading ? <RefreshCw className="w-5 h-5 animate-spin" /> : <Search className="w-5 h-5" />}
                    </button>
                    <button type="button" onClick={handleReset} disabled={loading} className="bg-slate-800 border border-slate-700 hover:bg-rose-500 hover:border-rose-500 text-slate-400 hover:text-white px-4 py-2 rounded-xl font-bold flex items-center justify-center transition-colors shadow-lg disabled:opacity-50 shrink-0" title="초기화">
                        <RotateCcw className="w-5 h-5" />
                    </button>
                </form>
            </header>
            
            <div className="mb-6 text-sky-400 font-bold text-lg flex items-center gap-2">
                현재 키워드: <span className="bg-sky-500/10 px-3 py-1 rounded-lg border border-sky-500/20">{newsTab === 'global' ? currentGlobalQuery : currentKoreaQuery}</span>
            </div>

            {loading ? <div className="text-center text-slate-400 mt-20 text-lg font-bold animate-pulse">뉴스를 수집 중입니다...</div> : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 pb-20">
                    {newsTab === 'global' ? (
                        globalNews.length > 0 ? globalNews.map((news, idx) => {
                            const imgSrc = getThumbnail(news.url, news.image);
                            const isLogo = !imgSrc.includes('unsplash'); // 배경사진이 아닌 언론사 로고인 경우
                            return (
                                <motion.a key={idx} href={news.url} target="_blank" rel="noopener noreferrer" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: idx * 0.03 }} className="bg-slate-800/50 border border-slate-700/50 rounded-2xl overflow-hidden hover:border-sky-500 hover:-translate-y-1 hover:shadow-xl hover:shadow-sky-900/20 transition-all flex flex-col cursor-pointer group">
                                    <div className={`w-full h-48 overflow-hidden bg-slate-900 border-b border-slate-700/50 flex items-center justify-center ${isLogo ? 'p-8' : ''}`}>
                                        <img src={imgSrc} alt="news" className={`w-full h-full ${isLogo ? 'object-contain' : 'object-cover'} group-hover:scale-105 transition-transform duration-300`} />
                                    </div>
                                    <div className="p-5 flex-1 flex flex-col justify-between">
                                        <div>
                                            <h3 className="text-lg font-bold text-white line-clamp-2 mb-2 leading-snug group-hover:text-sky-400 transition-colors">{news.headline}</h3>
                                            <p className="text-sm text-slate-400 line-clamp-3 leading-relaxed">{news.summary}</p>
                                        </div>
                                        <div className="text-xs text-sky-400 mt-4 font-bold text-right">자세히 보기 ➔</div>
                                    </div>
                                </motion.a>
                            );
                        }) : <div className="text-slate-500 col-span-full text-center py-10 font-bold bg-slate-800/20 rounded-2xl border border-slate-800">검색된 해외 뉴스가 없습니다.</div>
                    ) : (
                        koreaNews.length > 0 ? koreaNews.map((news, idx) => {
                            const imgSrc = getThumbnail(news.link);
                            const isLogo = !imgSrc.includes('unsplash');
                            return (
                                <motion.a key={idx} href={news.link} target="_blank" rel="noopener noreferrer" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: idx * 0.03 }} className="bg-slate-800/50 border border-slate-700/50 rounded-2xl overflow-hidden hover:border-sky-500 hover:-translate-y-1 hover:shadow-xl hover:shadow-sky-900/20 transition-all flex flex-col cursor-pointer group">
                                    <div className={`w-full h-48 overflow-hidden bg-slate-900 border-b border-slate-700/50 flex items-center justify-center ${isLogo ? 'p-8' : ''}`}>
                                        <img src={imgSrc} alt="news" className={`w-full h-full ${isLogo ? 'object-contain' : 'object-cover'} group-hover:scale-105 transition-transform duration-300`} />
                                    </div>
                                    <div className="p-5 flex-1 flex flex-col justify-between">
                                        <div>
                                            <h3 className="text-lg font-bold text-white line-clamp-2 mb-3 leading-snug group-hover:text-sky-400 transition-colors" dangerouslySetInnerHTML={{ __html: news.title.replace(/<[^>]*>?/gm, '') }}></h3>
                                            <p className="text-sm text-slate-400 line-clamp-3 leading-relaxed" dangerouslySetInnerHTML={{ __html: news.description.replace(/<[^>]*>?/gm, '') }}></p>
                                        </div>
                                        <div className="text-xs text-sky-400 mt-4 font-bold text-right">자세히 보기 ➔</div>
                                    </div>
                                </motion.a>
                            );
                        }) : <div className="text-slate-500 col-span-full text-center py-10 font-bold bg-slate-800/20 rounded-2xl border border-slate-800">검색된 국내 뉴스가 없습니다.</div>
                    )}
                </div>
            )}
        </div>
    );
}