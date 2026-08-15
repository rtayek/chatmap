package chatmap.presentation.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import chatmap.presentation.cli.HandoffOrchestratorCli.Options;

class HandoffOrchestratorCliTest {

    private static final Options NO_DEFAULTS = new Options(null, null, null, false);

    @Test
    void explicitFlagsAreUsedWhenNoDefaultsExist() {
        Options options = HandoffOrchestratorCli.parse(
                new String[] {"--inbox", "in", "--registry", "reg.properties"}, NO_DEFAULTS);

        assertEquals(Path.of("in").toAbsolutePath().normalize(), options.inbox());
        assertEquals(Path.of("reg.properties").toAbsolutePath().normalize(), options.registry());
        assertNull(options.intervalSeconds());
        assertEquals(false, options.autoPush());
    }

    @Test
    void fallsBackToDefaultsWhenNoFlagsGiven() {
        Options defaults = new Options(Path.of("default-inbox"), Path.of("default-registry"), 60L, true);

        Options options = HandoffOrchestratorCli.parse(new String[0], defaults);

        assertEquals(Path.of("default-inbox").toAbsolutePath().normalize(), options.inbox());
        assertEquals(Path.of("default-registry").toAbsolutePath().normalize(), options.registry());
        assertEquals(60L, options.intervalSeconds());
        assertTrue(options.autoPush());
    }

    @Test
    void explicitFlagsOverrideDefaultsFieldByField() {
        Options defaults = new Options(Path.of("default-inbox"), Path.of("default-registry"), 60L, true);

        Options options = HandoffOrchestratorCli.parse(new String[] {"--inbox", "override-inbox"}, defaults);

        assertEquals(Path.of("override-inbox").toAbsolutePath().normalize(), options.inbox());
        assertEquals(Path.of("default-registry").toAbsolutePath().normalize(), options.registry());
        assertEquals(60L, options.intervalSeconds());
        assertTrue(options.autoPush());
    }

    @Test
    void missingInboxAndRegistryWithNoDefaultsReturnsNull() {
        assertNull(HandoffOrchestratorCli.parse(new String[0], NO_DEFAULTS));
    }

    @Test
    void missingRegistryWithOnlyInboxDefaultReturnsNull() {
        Options defaults = new Options(Path.of("default-inbox"), null, null, false);

        assertNull(HandoffOrchestratorCli.parse(new String[0], defaults));
    }

    @Test
    void unknownFlagReturnsNull() {
        assertNull(HandoffOrchestratorCli.parse(
                new String[] {"--inbox", "in", "--registry", "reg", "--bogus"}, NO_DEFAULTS));
    }

    @Test
    void autoPushDefaultsToTrueWhenNotSpecifiedInProperties() {
        Properties properties = new Properties();
        properties.setProperty("inbox", "in");
        properties.setProperty("registry", "reg.properties");

        Options options = HandoffOrchestratorCli.optionsFromProperties(properties);

        assertTrue(options.autoPush());
    }

    @Test
    void autoPushExplicitFalseInPropertiesIsHonored() {
        Properties properties = new Properties();
        properties.setProperty("inbox", "in");
        properties.setProperty("registry", "reg.properties");
        properties.setProperty("autoPush", "false");

        Options options = HandoffOrchestratorCli.optionsFromProperties(properties);

        assertFalse(options.autoPush());
    }

    @Test
    void autoPushExplicitTrueInPropertiesIsHonored() {
        Properties properties = new Properties();
        properties.setProperty("autoPush", "true");

        Options options = HandoffOrchestratorCli.optionsFromProperties(properties);

        assertTrue(options.autoPush());
    }
}
