import { classifySentimentColor, humanizeEnum, formatSentimentScore } from "../format.js";

export function sentimentPanelHtml(aggregate) {
  const total = aggregate.articleCount || 0;
  const color = classifySentimentColor(aggregate.overallSentiment);
  const pct = (count) => (total > 0 ? (count / total) * 100 : 0);

  if (total === 0) {
    return `
      <div class="sentiment-panel">
        <div class="sentiment-overall ${color}">Overall: ${humanizeEnum(aggregate.overallSentiment)}</div>
        <p class="section-status">No analyzed articles yet for this symbol.</p>
      </div>`;
  }

  return `
    <div class="sentiment-panel">
      <div class="sentiment-overall ${color}">Overall: ${humanizeEnum(aggregate.overallSentiment)}</div>
      <div class="sentiment-sub">Based on the ${total} most recently analyzed article${total === 1 ? "" : "s"} · avg score ${formatSentimentScore(aggregate.averageScore)}</div>
      <div class="sentiment-bar">
        <span class="sentiment-bar-segment positive" style="width:${pct(aggregate.positiveCount)}%"></span>
        <span class="sentiment-bar-segment neutral" style="width:${pct(aggregate.neutralCount)}%"></span>
        <span class="sentiment-bar-segment negative" style="width:${pct(aggregate.negativeCount)}%"></span>
      </div>
      <div class="sentiment-counts">
        <span class="positive">Positive ${aggregate.positiveCount}</span>
        <span class="neutral">Neutral ${aggregate.neutralCount}</span>
        <span class="negative">Negative ${aggregate.negativeCount}</span>
      </div>
      <p class="sentiment-disclaimer">News sentiment describes the tone of recent coverage. It is not a prediction of future price movement.</p>
    </div>`;
}
