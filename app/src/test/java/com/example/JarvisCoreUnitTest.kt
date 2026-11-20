package com.example

import com.example.assistant.MathEvaluator
import com.example.data.model.PreferredLanguage
import com.example.data.model.TaskCategory
import com.example.data.remote.OpenAiChatClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisCoreUnitTest {
    @Test
    fun `math evaluator handles English and Hindi expressions`() {
        val sum = MathEvaluator.tryExtractAndEvaluate("2500 + 1500")
        assertNotNull(sum)
        assertEquals(4000.0, sum!!, 0.001)

        val hindiPercent = MathEvaluator.tryExtractAndEvaluate("2500 का 18 प्रतिशत")
        assertNotNull(hindiPercent)
        assertEquals(450.0, hindiPercent!!, 0.001)

        val sqrtExpr = MathEvaluator.tryExtractAndEvaluate("sqrt(144) + 8")
        assertNotNull(sqrtExpr)
        assertEquals(20.0, sqrtExpr!!, 0.001)

        assertNull(MathEvaluator.tryExtractAndEvaluate("Hello Jarvis how are you"))
    }

    @Test
    fun `enums deserialize safely on unknown schema values`() {
        assertEquals(PreferredLanguage.BILINGUAL, PreferredLanguage.fromRaw("UNKNOWN_LANG"))
        assertEquals(TaskCategory.TASK, TaskCategory.fromRaw(null))
    }

    @Test
    fun `openAi client detects proxy configuration`() {
        val client = OpenAiChatClient()
        assertTrue(client.isConfigured("https://jarvis-proxy.example.com"))
        assertFalse(client.isConfigured("http://insecure.example.com"))
    }
}
