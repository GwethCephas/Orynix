export function metricCardHtml({ label, value, sublabel, colorClass }) {
  return `
    <div class="metric-card">
      <div class="metric-label">${label}</div>
      <div class="metric-value ${colorClass || ""}">${value}</div>
      ${sublabel ? `<div class="metric-sublabel">${sublabel}</div>` : ""}
    </div>`;
}
