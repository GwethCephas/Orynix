package com.cephCoding.orynix.news.provider.massive

import com.cephCoding.orynix.news.provider.massive.dto.MassiveNewsResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class MassiveNewsDtoTest {

    private val mapper = JsonMapper()

    @Test
    fun `deserializes a full article`() {
        val json = """
            {
              "results": [
                {
                  "id": "abc123",
                  "title": "Apple unveils new product",
                  "author": "Jane Reporter",
                  "description": "A description of the article.",
                  "article_url": "https://example.com/article",
                  "published_utc": "2026-01-02T15:04:05Z",
                  "publisher": { "name": "Example News" }
                }
              ],
              "status": "OK"
            }
        """.trimIndent()

        val response = mapper.readValue(json, MassiveNewsResponse::class.java)

        assertEquals(1, response.results?.size)
        val article = response.results!![0]
        assertEquals("abc123", article.id)
        assertEquals("Apple unveils new product", article.title)
        assertEquals("Jane Reporter", article.author)
        assertEquals("A description of the article.", article.description)
        assertEquals("https://example.com/article", article.articleUrl)
        assertEquals("2026-01-02T15:04:05Z", article.publishedUtc)
        assertEquals("Example News", article.publisher?.name)
    }

    @Test
    fun `missing optional fields deserialize as null without error`() {
        val json = """
            {
              "results": [
                {
                  "id": "abc123",
                  "title": "Headline only",
                  "published_utc": "2026-01-02T15:04:05Z"
                }
              ]
            }
        """.trimIndent()

        val response = mapper.readValue(json, MassiveNewsResponse::class.java)

        val article = response.results!![0]
        assertEquals("abc123", article.id)
        assertEquals("Headline only", article.title)
        assertNull(article.author)
        assertNull(article.description)
        assertNull(article.articleUrl)
        assertNull(article.publisher)
    }

    @Test
    fun `missing id does not fail deserialization`() {
        val json = """
            {
              "results": [
                { "title": "No id here", "published_utc": "2026-01-02T15:04:05Z" }
              ]
            }
        """.trimIndent()

        val response = mapper.readValue(json, MassiveNewsResponse::class.java)

        assertNull(response.results!![0].id)
    }

    @Test
    fun `empty results array deserializes to an empty list`() {
        val json = """{ "results": [], "status": "OK" }"""

        val response = mapper.readValue(json, MassiveNewsResponse::class.java)

        assertEquals(0, response.results?.size)
    }

    @Test
    fun `missing results field deserializes to null, not an exception`() {
        val json = """{ "status": "OK" }"""

        val response = mapper.readValue(json, MassiveNewsResponse::class.java)

        assertNull(response.results)
    }
}
