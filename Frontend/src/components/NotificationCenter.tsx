import { useEffect, useRef, useState } from 'react';
import { Bell, CheckCheck, X } from 'lucide-react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { API_URL } from '../config';
import { getAuthHeaders, getStoredToken } from '../auth';

type NotificationItem = {
    id: number;
    type: string;
    message: string;
    createdAt: string;
    readAt?: string | null;
    read?: boolean;
};

export default function NotificationCenter() {
    const username =
        localStorage.getItem('username') || 'Guest';
    const isGuest = username === 'Guest';
    const [isOpen, setIsOpen] = useState(false);
    const [items, setItems] = useState<NotificationItem[]>([]);
    const [unread, setUnread] = useState(0);
    const clientRef = useRef<Client | null>(null);

    const load = async () => {
        if (isGuest || !getStoredToken()) return;

        try {
            const [itemsRes, countRes] = await Promise.all([
                fetch(`${API_URL}/api/notifications`, {
                    headers: getAuthHeaders(false)
                }),
                fetch(
                    `${API_URL}/api/notifications/unread-count`,
                    { headers: getAuthHeaders(false) }
                )
            ]);

            if (itemsRes.ok) {
                setItems(await itemsRes.json());
            }
            if (countRes.ok) {
                const data = await countRes.json();
                setUnread(Number(data.count) || 0);
            }
        } catch {
            // 실시간 재연결/다음 조회에서 복구합니다.
        }
    };

    useEffect(() => {
        load();

        const onFocus = () => load();
        window.addEventListener('focus', onFocus);

        const token = getStoredToken();
        if (!isGuest && token) {
            const client = new Client({
                webSocketFactory: () =>
                    new SockJS(`${API_URL}/ws-stomp`) as any,
                connectHeaders: {
                    Authorization: `Bearer ${token}`
                },
                reconnectDelay: 5000,
                debug: () => {},
                onConnect: () => {
                    client.subscribe(
                        `/topic/alerts/${username}`,
                        frame => {
                            try {
                                const incoming =
                                    JSON.parse(frame.body);
                                setItems(prev => [
                                    incoming,
                                    ...prev.filter(
                                        item =>
                                            item.id !== incoming.id
                                    )
                                ].slice(0, 50));
                                setUnread(prev => prev + 1);
                            } catch {
                                load();
                            }
                        }
                    );
                }
            });

            client.activate();
            clientRef.current = client;
        }

        return () => {
            window.removeEventListener('focus', onFocus);
            clientRef.current?.deactivate();
            clientRef.current = null;
        };
    }, [username, isGuest]);

    if (isGuest) return null;

    const markRead = async (item: NotificationItem) => {
        if (item.readAt || item.read) return;

        const res = await fetch(
            `${API_URL}/api/notifications/${item.id}/read`,
            {
                method: 'PATCH',
                headers: getAuthHeaders(false)
            }
        );

        if (res.ok) {
            setItems(prev =>
                prev.map(current =>
                    current.id === item.id
                        ? {
                            ...current,
                            read: true,
                            readAt:
                                new Date().toISOString()
                        }
                        : current
                )
            );
            setUnread(prev => Math.max(0, prev - 1));
        }
    };

    const markAllRead = async () => {
        const res = await fetch(
            `${API_URL}/api/notifications/read-all`,
            {
                method: 'POST',
                headers: getAuthHeaders(false)
            }
        );

        if (res.ok) {
            setItems(prev =>
                prev.map(item => ({
                    ...item,
                    read: true,
                    readAt:
                        item.readAt
                        || new Date().toISOString()
                }))
            );
            setUnread(0);
        }
    };

    const formatKst = (value: string) => {
        const normalized =
            /[zZ]|[+-]\d{2}:\d{2}$/.test(value)
                ? value
                : `${value}+09:00`;
        const date = new Date(normalized);
        if (Number.isNaN(date.getTime())) return value;

        return new Intl.DateTimeFormat('ko-KR', {
            timeZone: 'Asia/Seoul',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            hour12: false
        }).format(date);
    };

    return (
        <div className="fixed top-4 right-4 z-[120]">
            <button
                type="button"
                onClick={() => setIsOpen(prev => !prev)}
                className="relative w-11 h-11 rounded-full bg-slate-800/95 border border-slate-700 text-slate-200 hover:text-sky-400 shadow-xl flex items-center justify-center"
                aria-label="알림센터"
            >
                <Bell className="w-5 h-5" />
                {unread > 0 && (
                    <span className="absolute -top-1 -right-1 min-w-5 h-5 px-1 rounded-full bg-rose-500 text-white text-[11px] font-bold flex items-center justify-center">
                        {unread > 99 ? '99+' : unread}
                    </span>
                )}
            </button>

            {isOpen && (
                <div className="absolute right-0 mt-3 w-[340px] max-h-[480px] bg-slate-900 border border-slate-700 rounded-2xl shadow-2xl overflow-hidden">
                    <div className="flex items-center justify-between p-4 border-b border-slate-800">
                        <div>
                            <div className="font-black text-white">
                                알림
                            </div>
                            <div className="text-xs text-slate-500">
                                읽지 않은 알림 {unread}개
                            </div>
                        </div>
                        <div className="flex items-center gap-1">
                            <button
                                type="button"
                                onClick={markAllRead}
                                className="p-2 text-slate-400 hover:text-emerald-400"
                                title="모두 읽음"
                            >
                                <CheckCheck className="w-4 h-4" />
                            </button>
                            <button
                                type="button"
                                onClick={() => setIsOpen(false)}
                                className="p-2 text-slate-400 hover:text-white"
                            >
                                <X className="w-4 h-4" />
                            </button>
                        </div>
                    </div>

                    <div className="overflow-y-auto max-h-[400px]">
                        {items.length === 0 ? (
                            <div className="p-8 text-center text-sm text-slate-500">
                                아직 알림이 없습니다.
                            </div>
                        ) : (
                            items.map(item => {
                                const isRead =
                                    Boolean(item.readAt || item.read);
                                return (
                                    <button
                                        key={item.id}
                                        type="button"
                                        onClick={() => markRead(item)}
                                        className={`w-full text-left p-4 border-b border-slate-800/80 hover:bg-slate-800/60 transition-colors ${isRead ? 'opacity-60' : 'bg-sky-500/5'}`}
                                    >
                                        <div className="flex items-start gap-3">
                                            <span
                                                className={`mt-1 w-2 h-2 rounded-full shrink-0 ${isRead ? 'bg-slate-600' : 'bg-sky-400'}`}
                                            />
                                            <div className="min-w-0">
                                                <div className="text-[11px] uppercase tracking-wide text-sky-400 font-bold mb-1">
                                                    {item.type}
                                                </div>
                                                <div className="text-sm text-slate-200 break-words">
                                                    {item.message}
                                                </div>
                                                <div className="text-[11px] text-slate-500 mt-2">
                                                    {formatKst(
                                                        item.createdAt
                                                    )}
                                                </div>
                                            </div>
                                        </div>
                                    </button>
                                );
                            })
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
