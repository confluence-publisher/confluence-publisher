package org.sahli.asciidoc.confluence.publisher.cli;

import org.junit.Test;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class ArgumentsParserTest {

    ArgumentsParser parser = new ArgumentsParser();

    @Test
    public void optionalArgument_valueNotPresent_returnsEmptyOptional() {
        String[] args = {};

        Optional<String> value = parser.optionalArgument("key", args);

        assertFalse(value.isPresent());
    }

    @Test
    public void optionalArgument_valuePresent_returnsValue() {
        String[] args = {"t=test", "t2=test2", "key=value"};

        Optional<String> value = parser.optionalArgument("key", args);

        assertTrue(value.isPresent());
        assertThat(value.get(), is("value"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void mandatoryArgument_valueNotPresent_throwsException() {
        String[] args = {};

        parser.mandatoryArgument("key", args);
    }

    @Test
    public void mandatoryArgument_valuePresent_returnsValue() {
        String[] args = {"t=test", "t2=test2", "key=value"};

        String value = parser.mandatoryArgument("key", args);

        assertThat(value, is("value"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void optionalJsonArgument_valueIsNotParseable_throwsException() {
        String[] args = {"t=test", "t2=test2", "key=value"};

        parser.optionalJsonArgument("key", args);
    }

    @Test
    public void optionalJsonArgument_valueNotPresent_returnsEmpty() {
        String[] args = {};

        Optional<Map<String, Object>> value = parser.optionalJsonArgument("key", args);

        assertFalse(value.isPresent());
    }

    @Test
    public void optionalJsonArgument_valuePresent_returnsParsedValue() {
        String[] args = {"t1=1", "t2=2", "key={\"attr1\":\"val1\", \"attr2\":\"val2\"}"};

        Optional<Map<String, Object>> value = parser.optionalJsonArgument("key", args);

        assertTrue(value.isPresent());
        assertThat(value.get().size(), is(2));
        assertThat(value.get().get("attr1"), is("val1"));
        assertThat(value.get().get("attr2"), is("val2"));
    }
}