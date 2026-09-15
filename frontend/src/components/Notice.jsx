/** A success / info / warning / error banner. */
export default function Notice({ type = 'info', title, children, onDismiss }) {
  return (
    <div className={`alert alert-${type}`} role={type === 'error' ? 'alert' : 'status'}>
      <div className="alert-body">
        {title && <strong className="alert-title">{title}</strong>}
        {children && <div>{children}</div>}
      </div>
      {onDismiss && (
        <div className="alert-actions">
          <button type="button" className="icon-btn" onClick={onDismiss} aria-label="Dismiss">
            ×
          </button>
        </div>
      )}
    </div>
  );
}
