import { useEffect, useRef, useState } from 'react';
import { formatCountdown } from '../utils/format';

const RETRY_MS = 3000;

/**
 * Counts down from the backend's `reservationSecondsRemaining`. It never decides that a reservation expired:
 * at zero it calls `onExpire` so the parent can re-fetch the order, and keeps asking every few seconds until
 * the backend reports a new status (the parent then stops rendering the timer).
 */
export default function ReservationTimer({ secondsRemaining, totalSeconds, onExpire, compact = false }) {
  const [remaining, setRemaining] = useState(Math.max(0, secondsRemaining ?? 0));
  const onExpireRef = useRef(onExpire);
  onExpireRef.current = onExpire;

  useEffect(() => {
    const start = Math.max(0, secondsRemaining ?? 0);
    const deadline = Date.now() + start * 1000;
    let countdown = null;
    let retry = null;

    const reachedZero = () => {
      onExpireRef.current?.();
      retry = setInterval(() => onExpireRef.current?.(), RETRY_MS);
    };

    setRemaining(start);
    if (start === 0) {
      reachedZero();
    } else {
      countdown = setInterval(() => {
        const left = Math.max(0, Math.round((deadline - Date.now()) / 1000));
        setRemaining(left);
        if (left === 0) {
          clearInterval(countdown);
          reachedZero();
        }
      }, 1000);
    }

    return () => {
      clearInterval(countdown);
      clearInterval(retry);
    };
  }, [secondsRemaining]);

  const urgency = remaining === 0 ? 'timer-done' : remaining <= 60 ? 'timer-urgent' : '';

  if (compact) {
    return (
      <span className={`timer-compact ${urgency}`} title="Reservation time left">
        ⏱ {remaining === 0 ? 'Expiring…' : formatCountdown(remaining)}
      </span>
    );
  }

  const percent = totalSeconds > 0 ? Math.min(100, (remaining / totalSeconds) * 100) : null;

  return (
    <div className={`timer ${urgency}`} role="timer" aria-live="off">
      <div className="timer-label">
        {remaining === 0 ? 'Checking reservation status…' : 'Reservation expires in'}
      </div>
      <div className="timer-value">{formatCountdown(remaining)}</div>
      {percent !== null && (
        <div className="timer-bar" aria-hidden="true">
          <div className="timer-bar-fill" style={{ width: `${percent}%` }} />
        </div>
      )}
    </div>
  );
}
