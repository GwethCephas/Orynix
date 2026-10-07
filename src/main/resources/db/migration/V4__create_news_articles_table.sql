CREATE TABLE news_articles (
    id UUID PRIMARY KEY,
    provider_article_id VARCHAR(255) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    title VARCHAR(1000) NOT NULL,
    description TEXT,
    source VARCHAR(255),
    url VARCHAR(2048),
    author VARCHAR(255),
    published_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_news_articles_symbol_provider_article_id UNIQUE (symbol, provider_article_id)
);

CREATE INDEX idx_news_articles_symbol_published_at ON news_articles (symbol, published_at);
