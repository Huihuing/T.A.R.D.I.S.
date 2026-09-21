import { API_URL } from '../config';
import { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { MessageCircle, Edit3, ArrowLeft, Send, Image as ImageIcon, Loader2, Search, ChevronLeft, ChevronRight } from 'lucide-react';
import { getAuthHeaders } from '../auth';

export default function Board() {
    const [viewMode, setViewMode] = useState<'list' | 'detail' | 'write'>('list');
    const [posts, setPosts] = useState<any[]>([]);
    const [selectedPost, setSelectedPost] = useState<any>(null);
    const [title, setTitle] = useState('');
    const [content, setContent] = useState('');
    const [commentInput, setCommentInput] = useState('');
    const [isUploading, setIsUploading] = useState(false);
    const [editingPostId, setEditingPostId] = useState<number | null>(null);
    const [guestNickname, setGuestNickname] = useState('');
    const [guestPassword, setGuestPassword] = useState('');
    const [commentGuestNickname, setCommentGuestNickname] = useState('');
    const [commentGuestPassword, setCommentGuestPassword] = useState('');
    const [searchInput, setSearchInput] = useState('');
    const [searchQuery, setSearchQuery] = useState('');
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [isListLoading, setIsListLoading] = useState(false);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const currentUser = localStorage.getItem('username');
    const isGuest = !currentUser || currentUser === 'Guest';
    
    const fetchPosts = async () => {
        if (viewMode !== 'list') return;

        setIsListLoading(true);
        try {
            const params = new URLSearchParams({
                page: String(page),
                size: '10'
            });
            if (searchQuery) params.set('q', searchQuery);

            const res = await fetch(
                `${API_URL}/api/board/posts?${params.toString()}`
            );
            const data = await res.json();
            if (!res.ok) {
                alert(data?.message || '게시글 목록을 불러오지 못했습니다.');
                return;
            }

            setPosts(Array.isArray(data.items) ? data.items : []);
            setTotalPages(Number(data.totalPages) || 0);
            setTotalElements(Number(data.totalElements) || 0);
        } catch {
            alert('게시글 목록을 불러오는 중 오류가 발생했습니다.');
        } finally {
            setIsListLoading(false);
        }
    };

    useEffect(() => {
        fetchPosts();
    }, [viewMode, page, searchQuery]);

    const submitSearch = (e: React.FormEvent) => {
        e.preventDefault();
        setPage(0);
        setSearchQuery(searchInput.trim());
    };

    const clearSearch = () => {
        setSearchInput('');
        setSearchQuery('');
        setPage(0);
    };

    const viewPostDetail = async (id: number) => {
        try {
            const res = await fetch(`${API_URL}/api/board/posts/${id}`);
            const data = await res.json();
            if (!res.ok) {
                alert(data?.message || '게시글을 불러오지 못했습니다.');
                return;
            }
            setSelectedPost(data);
            setViewMode('detail');
        } catch {
            alert('게시글을 불러오는 중 오류가 발생했습니다.');
        }
    };

    const submitPost = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            if (isGuest && (!guestPassword || (!editingPostId && !guestNickname.trim()))) {
                alert(editingPostId
                    ? '작성 비밀번호를 입력해주세요.'
                    : '비회원 닉네임과 작성 비밀번호를 입력해주세요.');
                return;
            }

            const payload: any = { title, content };
            if (isGuest) {
                if (!editingPostId) payload.guestNickname = guestNickname.trim();
                payload.guestPassword = guestPassword;
            }

            const res = await fetch(
                editingPostId
                    ? `${API_URL}/api/board/posts/${editingPostId}`
                    : `${API_URL}/api/board/posts`,
                {
                    method: editingPostId ? 'PUT' : 'POST',
                    headers: getAuthHeaders(),
                    body: JSON.stringify(payload)
                }
            );
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                alert(data?.message || '게시글 저장에 실패했습니다.');
                return;
            }

            setTitle('');
            setContent('');
            setGuestNickname('');
            setGuestPassword('');
            setEditingPostId(null);
            setViewMode('list');
        } catch {
            alert('게시글 저장 중 오류가 발생했습니다.');
        }
    };

    const submitComment = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!commentInput.trim()) return;

        try {
            if (isGuest && (!commentGuestNickname.trim() || !commentGuestPassword)) {
                alert('비회원 닉네임과 작성 비밀번호를 입력해주세요.');
                return;
            }

            const commentPayload: any = {
                postId: selectedPost.id,
                content: commentInput
            };
            if (isGuest) {
                commentPayload.guestNickname = commentGuestNickname.trim();
                commentPayload.guestPassword = commentGuestPassword;
            }

            const res = await fetch(`${API_URL}/api/board/comments`, {
                method: 'POST',
                headers: getAuthHeaders(),
                body: JSON.stringify(commentPayload)
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                alert(data?.message || '댓글 등록에 실패했습니다.');
                return;
            }

            setCommentInput('');
            setCommentGuestPassword('');
            viewPostDetail(selectedPost.id);
        } catch {
            alert('댓글 등록 중 오류가 발생했습니다.');
        }
    };

    // 💡 이미지 업로드 로직 (Spring Boot 백엔드 프록시 API 경유)
    const handleImageUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;

        setIsUploading(true);
        const formData = new FormData();
        formData.append('image', file);

        try {
            // CORS 에러 및 API 키 노출 방지를 위해 백엔드로 업로드를 요청합니다.
            const res = await fetch(`${API_URL}/api/board/upload`, {
                method: 'POST',
                headers: getAuthHeaders(false),
                body: formData
            });
            
            if (res.ok) {
                const data = await res.json();
                const imageUrl = data.url;
                setContent(prev => prev + `\n![업로드된 이미지](${imageUrl})\n`);
            } else {
                alert('이미지 업로드에 실패했습니다. (서버 응답 오류)');
            }
        } catch (error) {
            alert('이미지 업로드 중 오류가 발생했습니다.');
        } finally {
            setIsUploading(false);
            if (fileInputRef.current) fileInputRef.current.value = '';
        }
    };

    const startEditPost = () => {
        setTitle(selectedPost.title);
        setContent(selectedPost.content);
        setEditingPostId(selectedPost.id);
        setGuestNickname(selectedPost.guestNickname || '');
        setGuestPassword('');
        setViewMode('write');
    };

    const deletePost = async () => {
        if (!confirm('정말로 이 게시글을 삭제하시겠습니까?')) return;

        let password = '';
        if (selectedPost.isGuest) {
            password = window.prompt('작성할 때 입력한 비밀번호를 입력하세요.') || '';
            if (!password) return;
        }

        try {
            const res = await fetch(`${API_URL}/api/board/posts/${selectedPost.id}`, {
                method: 'DELETE',
                headers: getAuthHeaders(),
                body: JSON.stringify(selectedPost.isGuest
                    ? { guestPassword: password }
                    : {})
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                alert(data?.message || '게시글 삭제에 실패했습니다.');
                return;
            }
            setViewMode('list');
        } catch {
            alert('게시글 삭제 중 오류가 발생했습니다.');
        }
    };

    const deleteComment = async (comment: any) => {
        let password = '';
        if (comment.isGuest) {
            password = window.prompt('댓글 작성 비밀번호를 입력하세요.') || '';
            if (!password) return;
        }

        try {
            const res = await fetch(`${API_URL}/api/board/comments/${comment.id}`, {
                method: 'DELETE',
                headers: getAuthHeaders(),
                body: JSON.stringify(comment.isGuest
                    ? { guestPassword: password }
                    : {})
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                alert(data?.message || '댓글 삭제에 실패했습니다.');
                return;
            }
            viewPostDetail(selectedPost.id);
        } catch {
            alert('댓글 삭제 중 오류가 발생했습니다.');
        }
    };

    // 💡 수익률 첨부 버튼 로직
    const appendROI = async () => {
        const username = localStorage.getItem('username');
        if (!username || username === 'Guest') return alert('로그인이 필요합니다.');
        try {
            const res = await fetch(`${API_URL}/api/trade/portfolio`, { headers: getAuthHeaders(false) });
            if (!res.ok) return alert('포트폴리오를 불러오려면 다시 로그인해 주세요.');
            const portfolio = await res.json();
            const summary = portfolio.length > 0 
                ? portfolio.map((p: any) => `• ${p.symbol}: ${p.amount}주 (평단가 $${(p.averagePrice || 0).toFixed(2)})`).join('\n') 
                : '보유 주식이 없습니다.';
            setContent(prev => prev + `\n\n📊 **[나의 포트폴리오 현황]**\n${summary}\n`);
        } catch (e) {}
    };

    const formatKstDateTime = (value: string) => {
        if (!value) return '';
        const normalized = /[zZ]|[+-]\d{2}:\d{2}$/.test(value)
            ? value
            : `${value}+09:00`;

        const date = new Date(normalized);
        if (Number.isNaN(date.getTime())) {
            return value.substring(0, 16).replace('T', ' ');
        }

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

    // 💡 마크다운(이미지) 렌더링 함수
    const renderContent = (text: string) => {
        const parts = text.split(/(!\[.*?\]\(.*?\))/g);
        
        return parts.map((part, index) => {
            const imgMatch = part.match(/!\[(.*?)\]\((.*?)\)/);
            if (imgMatch) {
                const imageUrl = imgMatch[2].trim();
                if (!/^https?:\/\//i.test(imageUrl)) {
                    return <span key={index}>{part}</span>;
                }
                return <img key={index} src={imageUrl} alt={imgMatch[1]} className="rounded-xl max-w-full my-4 shadow-lg border border-slate-700/50" loading="lazy" referrerPolicy="no-referrer" />;
            }
            return <span key={index}>{part}</span>;
        });
    };

    return (
        <div className="p-4 sm:p-6 md:p-8 min-h-screen text-slate-200 bg-[#0b1120]">
            <div className="flex justify-between items-center mb-8 border-b border-slate-800 pb-4">
                <div>
                    <h1 className="text-3xl font-extrabold text-transparent bg-clip-text bg-gradient-to-r from-sky-400 to-indigo-400 flex items-center gap-3">
                        <MessageCircle className="w-8 h-8 text-sky-400" /> Community Board
                    </h1>
                    <p className="text-slate-400 text-sm mt-2">다른 투자자들과 인사이트를 공유하세요.</p>
                </div>
                {viewMode === 'list' && (
                    <button onClick={() => { setEditingPostId(null); setTitle(''); setContent(''); setViewMode('write'); }} className="bg-sky-600 hover:bg-sky-500 text-white px-5 py-2.5 rounded-xl font-bold flex items-center gap-2 shadow-lg transition-colors">
                        <Edit3 className="w-5 h-5" /> 글쓰기
                    </button>
                )}
            </div>

            <AnimatePresence mode="wait">
                {/* 1. 목록 화면 */}
                {viewMode === 'list' && (
                    <motion.div key="list" initial={{ opacity: 0, x: -20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }} className="flex flex-col gap-4">
                        <form onSubmit={submitSearch} className="flex flex-col sm:flex-row gap-2 mb-2">
                            <div className="flex-1 relative">
                                <Search className="w-4 h-4 text-slate-500 absolute left-4 top-1/2 -translate-y-1/2" />
                                <input
                                    type="search"
                                    value={searchInput}
                                    onChange={e => setSearchInput(e.target.value)}
                                    placeholder="제목, 내용, 작성자 검색"
                                    maxLength={100}
                                    className="w-full bg-slate-900 border border-slate-700 rounded-xl pl-11 pr-4 py-3 text-white outline-none focus:border-sky-500"
                                />
                            </div>
                            <button
                                type="submit"
                                className="bg-sky-600 hover:bg-sky-500 text-white px-5 py-3 rounded-xl font-bold"
                            >
                                검색
                            </button>
                            {searchQuery && (
                                <button
                                    type="button"
                                    onClick={clearSearch}
                                    className="bg-slate-700 hover:bg-slate-600 text-white px-4 py-3 rounded-xl font-bold"
                                >
                                    초기화
                                </button>
                            )}
                        </form>

                        <div className="text-xs text-slate-500 px-1">
                            {searchQuery
                                ? `"${searchQuery}" 검색 결과 ${totalElements}건`
                                : `전체 ${totalElements}건`}
                        </div>

                        {isListLoading && (
                            <div className="py-10 text-center text-slate-500">
                                게시글을 불러오는 중...
                            </div>
                        )}

                        {!isListLoading && posts.length === 0 && (
                            <div className="py-12 text-center text-slate-500 bg-slate-800/30 rounded-2xl border border-slate-700/50">
                                {searchQuery
                                    ? '검색 결과가 없습니다.'
                                    : '아직 게시글이 없습니다.'}
                            </div>
                        )}

                        {!isListLoading && posts.map((post) => (
                            <div key={post.id} onClick={() => viewPostDetail(post.id)} className="bg-slate-800/50 backdrop-blur-md p-6 rounded-3xl border border-slate-700/50 shadow-lg cursor-pointer hover:border-sky-500 hover:bg-slate-800 transition-all group">
                                <h3 className="text-xl font-bold text-white group-hover:text-sky-400 transition-colors">{post.title}</h3>
                                <div className="flex items-center gap-4 mt-3 text-sm text-slate-400 font-bold">
                                    <span>👤 {post.author}</span>
                                    <span>🕒 {formatKstDateTime(post.createdAt)}</span>
                                </div>
                            </div>
                        ))}

                        {totalPages > 1 && (
                            <div className="flex items-center justify-center gap-3 pt-4">
                                <button
                                    type="button"
                                    onClick={() => setPage(prev => Math.max(0, prev - 1))}
                                    disabled={page <= 0}
                                    className="p-2 rounded-lg bg-slate-800 border border-slate-700 text-slate-300 disabled:opacity-30"
                                    aria-label="이전 페이지"
                                >
                                    <ChevronLeft className="w-5 h-5" />
                                </button>
                                <span className="text-sm text-slate-400">
                                    {page + 1} / {totalPages}
                                </span>
                                <button
                                    type="button"
                                    onClick={() => setPage(prev => Math.min(totalPages - 1, prev + 1))}
                                    disabled={page >= totalPages - 1}
                                    className="p-2 rounded-lg bg-slate-800 border border-slate-700 text-slate-300 disabled:opacity-30"
                                    aria-label="다음 페이지"
                                >
                                    <ChevronRight className="w-5 h-5" />
                                </button>
                            </div>
                        )}
                    </motion.div>
                )}

                {/* 2. 글쓰기 화면 */}
                {viewMode === 'write' && (
                    <motion.div key="write" initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 20 }} className="bg-slate-800/50 p-6 rounded-3xl border border-slate-700/50 shadow-lg">
                        <button onClick={() => setViewMode('list')} className="flex items-center gap-2 text-slate-400 hover:text-white mb-6 font-bold"><ArrowLeft className="w-5 h-5" /> 목록으로</button>
                        <form onSubmit={submitPost} className="flex flex-col gap-4">
                            <input type="text" placeholder="제목을 입력하세요" value={title} onChange={e => setTitle(e.target.value)} maxLength={120} required className="w-full bg-slate-900 border border-slate-700 rounded-xl p-4 text-white font-bold outline-none focus:border-sky-500" />
                            
                            <div className="flex justify-between items-center bg-slate-900/50 p-3 rounded-xl border border-slate-700">
                                {isGuest ? (
                                    <div className="w-full grid grid-cols-1 sm:grid-cols-2 gap-2">
                                        {!editingPostId && (
                                            <input
                                                type="text"
                                                value={guestNickname}
                                                onChange={e => setGuestNickname(e.target.value)}
                                                minLength={2}
                                                maxLength={20}
                                                placeholder="비회원 닉네임 (2~20자)"
                                                required
                                                className="bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white outline-none focus:border-sky-500"
                                            />
                                        )}
                                        <input
                                            type="password"
                                            value={guestPassword}
                                            onChange={e => setGuestPassword(e.target.value)}
                                            minLength={4}
                                            maxLength={64}
                                            placeholder={editingPostId ? '작성 비밀번호' : '작성 비밀번호 (4~64자)'}
                                            required
                                            className="bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white outline-none focus:border-sky-500"
                                        />
                                    </div>
                                ) : (
                                    <>
                                        <div>
                                            <input 
                                                type="file" 
                                                accept="image/*" 
                                                className="hidden" 
                                                ref={fileInputRef}
                                                onChange={handleImageUpload}
                                            />
                                            <button 
                                                type="button" 
                                                onClick={() => fileInputRef.current?.click()} 
                                                disabled={isUploading}
                                                className="text-sm bg-slate-700 hover:bg-slate-600 text-white px-4 py-2 rounded-lg font-bold transition-colors flex items-center gap-2 disabled:opacity-50"
                                            >
                                                {isUploading ? <Loader2 className="w-4 h-4 animate-spin" /> : <ImageIcon className="w-4 h-4" />}
                                                {isUploading ? '업로드 중...' : '📷 이미지 첨부'}
                                            </button>
                                        </div>
                                        <button type="button" onClick={appendROI} className="text-sm bg-indigo-500/20 text-indigo-400 hover:bg-indigo-500 hover:text-white px-4 py-2 rounded-lg font-bold transition-colors">
                                            📊 내 포트폴리오 자랑하기
                                        </button>
                                    </>
                                )}
                            </div>
                            
                            <textarea placeholder="내용을 작성해 주세요... (마크다운 이미지 문법을 지원합니다)" value={content} onChange={e => setContent(e.target.value)} maxLength={20000} required rows={10} className="w-full bg-slate-900 border border-slate-700 rounded-xl p-4 text-white outline-none focus:border-sky-500 custom-scrollbar leading-relaxed" />
                            <button type="submit" disabled={isUploading} className="w-full bg-sky-600 hover:bg-sky-500 text-white font-bold py-4 rounded-xl shadow-lg mt-2 transition-colors disabled:opacity-50">
                                {editingPostId ? '게시글 수정' : '게시글 등록'}
                            </button>
                        </form>
                    </motion.div>
                )}

                {/* 3. 상세 조회 및 댓글 화면 */}
                {viewMode === 'detail' && selectedPost && (
                    <motion.div key="detail" initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.95 }} className="bg-slate-800/50 p-6 md:p-10 rounded-3xl border border-slate-700/50 shadow-lg">
                        <div className="flex justify-between items-center mb-6">
                            <button onClick={() => setViewMode('list')} className="flex items-center gap-2 text-slate-400 hover:text-white font-bold"><ArrowLeft className="w-5 h-5" /> 목록으로</button>
                            {(selectedPost.author === currentUser || selectedPost.isGuest) && (
                                <div className="flex gap-2">
                                    <button onClick={startEditPost} className="text-sm bg-slate-700 hover:bg-slate-600 px-3 py-1.5 rounded-lg text-white font-bold transition-colors">수정</button>
                                    <button onClick={deletePost} className="text-sm bg-red-600/20 hover:bg-red-600/40 text-red-400 px-3 py-1.5 rounded-lg font-bold transition-colors">삭제</button>
                                </div>
                            )}
                        </div>
                        <h2 className="text-3xl font-black text-white mb-4">{selectedPost.title}</h2>
                        <div className="flex gap-4 text-sm text-sky-400 font-bold border-b border-slate-700 pb-6 mb-6">
                            <span>작성자: {selectedPost.author}</span>
                            <span>작성일: {formatKstDateTime(selectedPost.createdAt)}</span>
                        </div>
                        
                        <div className="text-slate-200 leading-relaxed whitespace-pre-wrap min-h-[150px] text-lg">
                            {renderContent(selectedPost.content)}
                        </div>

                        {/* 댓글 영역 */}
                        <div className="mt-12 border-t border-slate-700 pt-8">
                            <h3 className="text-xl font-bold text-white mb-6">💬 댓글 ({selectedPost.comments.length})</h3>
                            <div className="flex flex-col gap-4 mb-6">
                                {selectedPost.comments.map((c: any) => (
                                    <div key={c.id} className="bg-slate-900/50 p-4 rounded-2xl border border-slate-700">
                                        <div className="flex justify-between items-center mb-2">
                                            <span className="font-bold text-sky-400 text-sm">{c.author}</span>
                                            <div className="flex items-center gap-2">
                                                <span className="text-xs text-slate-500">{formatKstDateTime(c.createdAt)}</span>
                                                {(c.author === currentUser || c.isGuest) && (
                                                    <button
                                                        type="button"
                                                        onClick={() => deleteComment(c)}
                                                        className="text-xs text-rose-400 hover:text-rose-300"
                                                    >
                                                        삭제
                                                    </button>
                                                )}
                                            </div>
                                        </div>
                                        <p className="text-sm text-slate-300">{c.content}</p>
                                    </div>
                                ))}
                            </div>
                            <form onSubmit={submitComment} className="flex flex-col gap-2">
                                {isGuest && (
                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                                        <input
                                            type="text"
                                            value={commentGuestNickname}
                                            onChange={e => setCommentGuestNickname(e.target.value)}
                                            minLength={2}
                                            maxLength={20}
                                            placeholder="비회원 닉네임"
                                            required
                                            className="bg-slate-900 border border-slate-700 text-white px-4 py-3 rounded-xl outline-none focus:border-sky-500"
                                        />
                                        <input
                                            type="password"
                                            value={commentGuestPassword}
                                            onChange={e => setCommentGuestPassword(e.target.value)}
                                            minLength={4}
                                            maxLength={64}
                                            placeholder="댓글 비밀번호"
                                            required
                                            className="bg-slate-900 border border-slate-700 text-white px-4 py-3 rounded-xl outline-none focus:border-sky-500"
                                        />
                                    </div>
                                )}
                                <div className="flex gap-2">
                                    <input type="text" value={commentInput} onChange={e => setCommentInput(e.target.value)} maxLength={3000} placeholder="댓글을 남겨보세요..." className="flex-1 bg-slate-900 border border-slate-700 text-white px-4 py-3 rounded-xl outline-none focus:border-sky-500 transition-colors" />
                                    <button type="submit" disabled={!commentInput.trim()} className="bg-sky-600 hover:bg-sky-500 text-white px-5 rounded-xl font-bold transition-colors disabled:opacity-50"><Send className="w-5 h-5" /></button>
                                </div>
                            </form>
                        </div>
                    </motion.div>
                )}
            </AnimatePresence>
        </div>
    );
}