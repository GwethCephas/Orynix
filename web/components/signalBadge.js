import { classifySignalColor, humanizeEnum, formatNumber } from "../format.js";

export function signalBadgeHtml(signal) {
  const color = classifySignalColor(signal.state);
  const valueHtml =
    signal.value !== null && signal.value !== undefined
      ? `<span class="signal-value">${formatNumber(signal.value, 2)}</span>`
      : "";

  return `
    <div class="signal-badge ${color}">
      <span class="signal-type">${humanizeEnum(signal.type)}</span>
      <span class="signal-state">${humanizeEnum(signal.state)}</span>
      ${valueHtml}
    </div>`;
}
