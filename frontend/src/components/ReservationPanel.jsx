import { Link } from 'react-router-dom';
import ReservationTimer from './ReservationTimer';
import { formatTime } from '../utils/format';

/** Countdown while an order is RESERVED, or a clear "Reservation Expired" message once the backend expires it. */
export default function ReservationPanel({ order, onExpire }) {
  if (order.status === 'RESERVED' && order.reservationSecondsRemaining != null) {
    const totalSeconds =
      order.createdAt && order.expiresAt ? (new Date(order.expiresAt) - new Date(order.createdAt)) / 1000 : null;

    return (
      <div className="reservation reservation-active">
        <div className="reservation-info">
          <strong>Stock reserved for this order</strong>
          <p>
            The items are held for you until <b>{formatTime(order.expiresAt)}</b>. Complete payment before the timer runs
            out, or the stock goes back to inventory.
          </p>
        </div>
        <ReservationTimer
          secondsRemaining={order.reservationSecondsRemaining}
          totalSeconds={totalSeconds}
          onExpire={onExpire}
        />
      </div>
    );
  }

  if (order.status === 'EXPIRED') {
    return (
      <div className="reservation reservation-expired">
        <div className="reservation-info">
          <strong>Reservation Expired</strong>
          <p>This order was not paid in time. The reserved stock has been released back to inventory.</p>
        </div>
        <Link to="/" className="btn btn-primary">
          Back to products
        </Link>
      </div>
    );
  }

  return null;
}
