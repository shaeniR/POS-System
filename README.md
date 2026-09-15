# POS-System

## Stock Reservation

- **Reserve on checkout** — `POST /api/orders/checkout/{cartId}` atomically deducts stock (products locked in ascending ID order) and creates an order in `RESERVED` status with `expiresAt = now + 5 minutes`. The response includes `reservationSecondsRemaining`.
- **Automatic expiry** — `ReservationExpiryScheduler` runs every 5 seconds, finds `RESERVED` orders past `expiresAt`, marks each `EXPIRED` and returns its quantities to available stock, each in its own transaction.
- **Immediate release** — fetching an order (`GET /api/orders/{id}` or `/number/{orderNumber}`) expires it on the spot if it is overdue, so no client sees a stale reservation between scheduler runs.
- **Released exactly once** — every release takes a `SELECT ... FOR UPDATE` lock on the order and re-checks its status, so concurrent attempts (scheduler, reads, multiple instances) cannot restore the same stock twice.
- **Stock overview** — `GET /api/products/stock` lists `availableStock` and `reservedStock` for every product.

Configuration (`application.properties`):

| Property | Default | Meaning |
|---|---|---|
| `pos.reservation.ttl-minutes` | `5` | How long a checkout holds its stock |
| `pos.reservation.expiry-check-interval-ms` | `5000` | How often expired reservations are released |
