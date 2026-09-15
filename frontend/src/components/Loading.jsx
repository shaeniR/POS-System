export default function Loading({ label = 'Loading…', inline = false }) {
  return (
    <div className={inline ? 'loading loading-inline' : 'loading'} role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}
