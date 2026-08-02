package presentation.bot

import com.ua.astrumon.domain.bot.model.BotLanguage
import java.util.PropertyResourceBundle
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the bundles against drift: a key added to one language and forgotten in the other would
 * otherwise surface as a MissingResourceException at runtime, in whichever chat picked that language.
 */
class MessageBundleParityTest {
    @Test
    fun `every language bundle should expose the same key set`() {
        val base = keysOf(BotLanguage.UK)

        BotLanguage.entries
            .filter { it != BotLanguage.UK }
            .forEach { language ->
                val other = keysOf(language)
                val missing = base - other
                val extra = other - base

                assertTrue(
                    missing.isEmpty() && extra.isEmpty(),
                    "Bundle '${resourceName(language)}' drifted from the base bundle. " +
                        "Missing: ${missing.sorted()}. Unexpected: ${extra.sorted()}.",
                )
            }
    }

    @Test
    fun `the base bundle should not be empty`() {
        assertTrue(keysOf(BotLanguage.UK).isNotEmpty(), "messages.properties carries no keys")
    }

    private fun keysOf(language: BotLanguage): Set<String> {
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream(resourceName(language))) {
            "Missing resource bundle: ${resourceName(language)}"
        }
        return stream.use { PropertyResourceBundle(it).keySet() }
    }

    /** Ukrainian lives in the base bundle — see [BotLanguage.UK] mapping to `Locale.ROOT`. */
    private fun resourceName(language: BotLanguage): String = if (language == BotLanguage.UK) {
        "messages.properties"
    } else {
        "messages_${language.code}.properties"
    }
}
