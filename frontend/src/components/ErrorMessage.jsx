import { parseApiError } from '../utils/errors';

/** Shows an API error as a friendly message. Pass `onRetry` to offer a retry button. */
export default function ErrorMessage({ error, onRetry, onDismiss, children }) {
  const parsed = parseApiError(error);
  if (!parsed) return null;

  return (
    <div className="alert alert-error" role="alert">
      <div className="alert-body">
        <strong className="alert-title">{parsed.title}</strong>
        <p>{parsed.message}</p>
        {parsed.detail && <p className="alert-detail">{parsed.detail}</p>}
        {children}
      </div>
      <div className="alert-actions">
        {onRetry && (
          <button type="button" className="btn btn-secondary btn-sm" onClick={onRetry}>
            Try again
          </button>
        )}
        {onDismiss && (
          <button type="button" className="icon-btn" onClick={onDismiss} aria-label="Dismiss">
            ×
          </button>
        )}
      </div>
    </div>
  );
}
