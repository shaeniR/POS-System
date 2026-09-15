# POS System — Frontend

React + Vite frontend for the **POS Order & Inventory System** Spring Boot backend.
All data comes from the backend REST API; there is no mock data.

## Run locally

1. Start the backend from the project root: `mvn spring-boot:run` (listens on port **8081**).
2. In this folder:

```bash
npm install
npm run dev
```

3. Open http://localhost:5173

### Backend URL

The API base URL is configured in one place, [src/services/api.js](src/services/api.js).

- **Development:** requests go to `/api` on the Vite dev server, which forwards them to `http://localhost:8081`.
  No CORS setup is needed. To point at another port, create `.env` with `VITE_BACKEND_URL=http://localhost:8080`.
- **Deployment:** set `VITE_API_BASE_URL=https://your-backend.example.com` before `npm run build`, and add the
  frontend's URL to `pos.cors.allowed-origins` in the backend.

## Pages

| Route | Page | What it shows |
|---|---|---|
| `/` | Products | Product cards, price, live stock, search, add to cart |
| `/cart` | Cart | Items, quantity +/−, remove, clear, total |
| `/checkout` | Checkout | Cart review → checkout → order confirmation with 5-minute countdown |
| `/payment/:orderId` | Payment | Mock gateway: SUCCESS / FAILURE / TIMEOUT and the result |
| `/orders` | Orders | All orders with status filters and live reservation timers |
| `/orders/:id` | Order details | Items, timeline data, allowed actions, payment history |
| `/inventory` | Inventory | Available vs reserved stock, auto-refreshing |
| `/admin/products` | Product Management | Create, edit, delete products with validation |
| `/concurrency-test` | Concurrency Test | Many buyers check out one product at once; verifies no overselling |

## How it maps to the requirements

- **Backend is the source of truth.** Stock, reservation expiry and order transitions are never decided in the browser.
  Pay / Cancel buttons appear only when the order's `allowedTransitions` include `PAID` / `CANCELLED`.
- **Reservation countdown** starts from `reservationSecondsRemaining`. At zero the page re-fetches the order, and the
  backend marks it `EXPIRED` and releases the stock.
- **Cart id** is created by `POST /api/cart` and stored in `localStorage` (`pos.cartId`).
- **Duplicate protection:** buttons are disabled while a request runs, and every payment screen sends an
  `Idempotency-Key` header.
- **Concurrency:** a `409 Insufficient Stock` shows a clear message and refreshes the stock on screen.

## Project structure

```
src/
├── components/   Reusable UI (Navbar, ProductCard, ReservationTimer, ConfirmDialog, …)
├── context/      CartContext — the backend cart shared across pages
├── hooks/        useFetch (loading data), useAction (running actions)
├── pages/        One file per route
├── services/     Axios instance and one service per API area
├── utils/        Error messages and formatting
├── App.jsx       Routes
├── main.jsx      Entry point
└── index.css     Styles
```

## Build

```bash
npm run build   # output in dist/
```
