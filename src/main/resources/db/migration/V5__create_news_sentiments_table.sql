-- symbol is deliberately NOT denormalized onto this table: aggregation already holds the relevant
-- article-id list from news_articles, so no read pattern here ever needs to query by symbol. Do not
-- add a symbol column or a symbol-based index without re-checking that assumption first.
CREATE TABLE news_sentiments (
    id UUID PRIMARY KEY,
    news_article_id UUID NOT NULL REFERENCES news_articles(id) ON DELETE CASCADE,
    sentiment VARCHAR(10) NOT NULL,
    score NUMERIC(5,4) NOT NULL,
    confidence NUMERIC(5,4) NOT NULL,
    analyzer VARCHAR(50) NOT NULL,
    model VARCHAR(100) NOT NULL,
    analyzed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_news_sentiments_article_analyzer_model UNIQUE (news_article_id, analyzer, model)
);
