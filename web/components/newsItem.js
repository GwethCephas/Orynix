import { escapeHtml, isSafeHttpUrl, formatRelativeTime, formatDateTime, classifySentimentColor, humanizeEnum } from "../format.js";
import { getArticleSentiment } from "../api.js";

export function newsItemHtml(article) {
  const safeUrl = isSafeHttpUrl(article.url) ? article.url : null;
  const titleHtml = escapeHtml(article.title);
  const titleContent = safeUrl
    ? `<a href="${safeUrl}" target="_blank" rel="noopener noreferrer" class="news-title-link">${titleHtml}</a>`
    : `<span class="news-title-link">${titleHtml}</span>`;
  const articleId = escapeHtml(article.id ?? "");

  return `
    <article class="news-item">
      <div class="news-item-header">
        <h3 class="news-title">${titleContent}</h3>
        <span class="news-date" title="${escapeHtml(formatDateTime(article.publishedAt))}">${escapeHtml(formatRelativeTime(article.publishedAt))}</span>
      </div>
      <div class="news-meta">
        ${article.source ? `<span class="news-source">${escapeHtml(article.source)}</span>` : ""}
        ${article.author ? `<span class="news-author">By ${escapeHtml(article.author)}</span>` : ""}
      </div>
      ${article.description ? `<p class="news-description">${escapeHtml(article.description)}</p>` : ""}
      <div class="news-sentiment-slot">
        ${
          article.id
            ? `<button type="button" class="sentiment-reveal-btn" data-article-id="${articleId}">Analyze sentiment</button>`
            : `<span class="section-status">Sentiment unavailable for this article.</span>`
        }
      </div>
    </article>`;
}

async function runSentimentAnalysis(slot, articleId) {
  slot.innerHTML = `<span class="section-status">Analyzing...</span>`;
  try {
    const result = await getArticleSentiment(articleId);
    const color = classifySentimentColor(result.sentiment);
    const confidencePercent = (Number(result.confidence) * 100).toFixed(0);
    slot.innerHTML = `
      <span class="sentiment-pill ${color}">${humanizeEnum(result.sentiment)}</span>
      <span class="sentiment-confidence">confidence ${confidencePercent}%</span>`;
  } catch {
    slot.innerHTML = `
      <span class="sentiment-pill failed">Analysis failed</span>
      <button type="button" class="retry-sentiment-btn">Retry</button>`;
    slot.querySelector(".retry-sentiment-btn")?.addEventListener("click", () => runSentimentAnalysis(slot, articleId));
  }
}

export function wireNewsItemSentiment(container) {
  container.querySelectorAll(".sentiment-reveal-btn").forEach((button) => {
    button.addEventListener("click", () => runSentimentAnalysis(button.parentElement, button.dataset.articleId));
  });
}
