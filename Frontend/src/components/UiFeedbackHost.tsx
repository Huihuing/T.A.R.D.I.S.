import { useEffect, useRef, useState } from 'react';
import {
    AlertTriangle,
    CheckCircle2,
    Info,
    XCircle
} from 'lucide-react';
import { subscribeUiFeedback } from '../uiFeedback';
import type {
    ConfirmFeedback,
    FeedbackTone,
    ToastFeedback
} from '../uiFeedback';

const toneMeta: Record<
    FeedbackTone,
    { className: string; icon: typeof Info }
> = {
    success: {
        className:
            'border-emerald-500/30 bg-emerald-500/10 text-emerald-200',
        icon: CheckCircle2
    },
    error: {
        className:
            'border-rose-500/30 bg-rose-500/10 text-rose-200',
        icon: XCircle
    },
    warning: {
        className:
            'border-amber-500/30 bg-amber-500/10 text-amber-200',
        icon: AlertTriangle
    },
    info: {
        className:
            'border-sky-500/30 bg-sky-500/10 text-sky-200',
        icon: Info
    }
};

export default function UiFeedbackHost() {
    const [toast, setToast] = useState<ToastFeedback | null>(null);
    const [confirm, setConfirm] =
        useState<ConfirmFeedback | null>(null);
    const toastTimer = useRef<number | null>(null);

    useEffect(() => {
        return subscribeUiFeedback(event => {
            if (event.kind === 'toast') {
                if (toastTimer.current !== null) {
                    window.clearTimeout(toastTimer.current);
                }
                setToast(event);
                toastTimer.current = window.setTimeout(() => {
                    setToast(null);
                    toastTimer.current = null;
                }, 3500);
                return;
            }

            setConfirm(current => {
                if (current) {
                    current.resolve(false);
                }
                return event;
            });
        });
    }, []);

    useEffect(() => {
        return () => {
            if (toastTimer.current !== null) {
                window.clearTimeout(toastTimer.current);
            }
        };
    }, []);

    const resolveConfirm = (accepted: boolean) => {
        setConfirm(current => {
            current?.resolve(accepted);
            return null;
        });
    };

    const ToastIcon = toast
        ? toneMeta[toast.tone].icon
        : Info;

    return (
        <>
            {toast && (
                <div
                    className={
                        'fixed right-4 top-4 z-[300] flex max-w-[calc(100vw-2rem)] items-start gap-3 rounded-2xl border px-4 py-3 shadow-2xl backdrop-blur-md '
                        + toneMeta[toast.tone].className
                    }
                    role="status"
                    aria-live="polite"
                >
                    <ToastIcon className="mt-0.5 h-5 w-5 shrink-0" />
                    <div className="text-sm font-bold leading-relaxed">
                        {toast.message}
                    </div>
                </div>
            )}

            {confirm && (
                <div
                    className="fixed inset-0 z-[310] flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm"
                    role="presentation"
                    onMouseDown={() => resolveConfirm(false)}
                >
                    <div
                        className="w-full max-w-md rounded-3xl border border-slate-700 bg-slate-900 p-6 shadow-2xl"
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="ui-confirm-title"
                        onMouseDown={event => event.stopPropagation()}
                    >
                        <div className="flex items-start gap-3">
                            <div
                                className={
                                    'flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border '
                                    + (confirm.danger
                                        ? 'border-rose-500/30 bg-rose-500/10 text-rose-300'
                                        : 'border-sky-500/30 bg-sky-500/10 text-sky-300')
                                }
                            >
                                <AlertTriangle className="h-5 w-5" />
                            </div>
                            <div>
                                <h2
                                    id="ui-confirm-title"
                                    className="text-lg font-black text-white"
                                >
                                    {confirm.title}
                                </h2>
                                <p className="mt-2 text-sm leading-relaxed text-slate-400">
                                    {confirm.message}
                                </p>
                            </div>
                        </div>

                        <div className="mt-6 flex gap-3">
                            <button
                                type="button"
                                onClick={() => resolveConfirm(false)}
                                className="flex-1 rounded-xl border border-slate-700 bg-slate-800 py-3 font-bold text-slate-300 hover:bg-slate-700"
                            >
                                {confirm.cancelLabel}
                            </button>
                            <button
                                type="button"
                                onClick={() => resolveConfirm(true)}
                                className={
                                    'flex-1 rounded-xl py-3 font-black '
                                    + (confirm.danger
                                        ? 'bg-rose-600 text-white hover:bg-rose-500'
                                        : 'bg-sky-500 text-slate-950 hover:bg-sky-400')
                                }
                            >
                                {confirm.confirmLabel}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </>
    );
}
