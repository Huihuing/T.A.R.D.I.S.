export type FeedbackTone =
    | 'success'
    | 'error'
    | 'warning'
    | 'info';

export type ToastFeedback = {
    kind: 'toast';
    id: number;
    message: string;
    tone: FeedbackTone;
};

export type ConfirmFeedback = {
    kind: 'confirm';
    id: number;
    title: string;
    message: string;
    confirmLabel: string;
    cancelLabel: string;
    danger: boolean;
    resolve: (accepted: boolean) => void;
};

export type UiFeedbackEvent =
    | ToastFeedback
    | ConfirmFeedback;

type Listener = (event: UiFeedbackEvent) => void;

const listeners = new Set<Listener>();
let sequence = 0;

function nextId(): number {
    sequence += 1;
    return sequence;
}

function emit(event: UiFeedbackEvent): void {
    listeners.forEach(listener => listener(event));
}

export function subscribeUiFeedback(listener: Listener): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
}

export function notify(
    message: string,
    tone: FeedbackTone = 'info'
): void {
    emit({
        kind: 'toast',
        id: nextId(),
        message,
        tone
    });
}

export function confirmAction(options: {
    title?: string;
    message: string;
    confirmLabel?: string;
    cancelLabel?: string;
    danger?: boolean;
}): Promise<boolean> {
    return new Promise(resolve => {
        emit({
            kind: 'confirm',
            id: nextId(),
            title: options.title || '확인',
            message: options.message,
            confirmLabel: options.confirmLabel || '확인',
            cancelLabel: options.cancelLabel || '취소',
            danger: Boolean(options.danger),
            resolve
        });
    });
}
