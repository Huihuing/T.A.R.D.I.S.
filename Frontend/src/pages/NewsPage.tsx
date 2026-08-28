import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';

interface NewsItem { id: number; headline: string; summary: string; url: string; image: string; datetime: number; }
interface NaverNewsItem { title: string; link: string; description: string; pubDate: string; }

export default function NewsPage() {
    const [globalNews, setGlobalNews] = useState<NewsItem[]>([]);
    const [koreaNews, setKoreaNews] = useState<NaverNewsItem[]>([]);
    const [newsTab, setNewsTab] = useState<'global' | 'korea'>('global');
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const fetchNews = async () => {
            setLoading(true);
            try {
                const resGlobal = await fetch(`http://localhost:8080/api/news/global?symbol=AAPL`);
                const globalData = await resGlobal.json();
                if (Array.isArray(globalData)) setGlobalNews(globalData);
                const resKorea = await fetch('http://localhost:8080/api/news/korea?query=증시 시황 특징주');
                const koreaData = await resKorea.json();
                if (koreaData && koreaData.items) setKoreaNews(koreaData.items);
            } catch (err) {}
            setLoading(false);
        };
        fetchNews();
    }, []);

    return (
        <div className="min-h-screen bg-slate-900 text-white p-8 font-sans">
            <header className="mb-8 border-b border-slate-700 pb-6 flex justify-between items-end">
                <div><h1 className="text-4xl font-extrabold text-sky-400">📰 Market News</h1><p className="text-slate-400 mt-2">최신 소식을 확인하세요.</p></div>
                <div className="flex bg-slate-800 rounded-lg p-1 border border-slate-700">
                    <button onClick={() => setNewsTab('global')} className={`px-6 py-2 rounded-md font-bold transition-all ${newsTab === 'global' ? 'bg-sky-500 text-white' : 'text-slate-400 hover:text-white'}`}>해외 증시</button>
                    <button onClick={() => setNewsTab('korea')} className={`px-6 py-2 rounded-md font-bold transition-all ${newsTab === 'korea' ? 'bg-sky-500 text-white' : 'text-slate-400 hover:text-white'}`}>국내 증시</button>
                </div>
            </header>
            {loading ? <div className="text-center text-slate-400 mt-20 text-xl font-bold animate-pulse">뉴스를 수집 중입니다...</div> : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 overflow-y-auto pb-20">
                    {newsTab === 'global' ? (
                        globalNews.map((news, idx) => (
                            <motion.a key={idx} href={news.url} target="_blank" rel="noopener noreferrer" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: idx * 0.03 }} className="bg-slate-800 border border-slate-700 rounded-xl overflow-hidden hover:border-sky-500 hover:shadow-xl hover:shadow-sky-900/20 transition-all flex flex-col cursor-pointer">
                                <img src={news.image || '/no-image.png'} alt="news" className="w-full h-48 object-cover bg-slate-900 border-b border-slate-700" onError={(e) => { e.currentTarget.src = '/no-image.png'; }} />
                                <div className="p-5 flex-1 flex flex-col justify-between">
                                    <div><h3 className="text-lg font-bold text-slate-100 line-clamp-2 mb-2 leading-snug">{news.headline}</h3><p className="text-sm text-slate-400 line-clamp-3">{news.summary}</p></div>
                                    <div className="text-xs text-sky-400 mt-4 font-bold">자세히 보기 ➔</div>
                                </div>
                            </motion.a>
                        ))
                    ) : (
                        koreaNews.map((news, idx) => (
                            <motion.a key={idx} href={news.link} target="_blank" rel="noopener noreferrer" initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: idx * 0.03 }} className="bg-slate-800 border border-slate-700 p-6 rounded-xl hover:border-sky-500 hover:shadow-xl hover:shadow-sky-900/20 transition-all flex flex-col cursor-pointer justify-between">
                                <div><h3 className="text-lg font-bold text-slate-100 line-clamp-2 mb-3 leading-snug" dangerouslySetInnerHTML={{ __html: news.title.replace(/<[^>]*>?/gm, '') }}></h3><p className="text-sm text-slate-400 line-clamp-4" dangerouslySetInnerHTML={{ __html: news.description.replace(/<[^>]*>?/gm, '') }}></p></div>
                                <div className="text-xs text-sky-400 mt-4 font-bold">자세히 보기 ➔</div>
                            </motion.a>
                        ))
                    )}
                </div>
            )}
        </div>
    );
}