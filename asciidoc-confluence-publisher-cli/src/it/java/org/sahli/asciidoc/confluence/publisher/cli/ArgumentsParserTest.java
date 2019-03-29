package org.sahli.asciidoc.confluence.publisher.cli;

import org.junit.Test;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class ArgumentsParserTest {

    @Test
    public void optionalArgument_valueNotPresent_returnsEmptyOptional() {
        // arrange
        String[] args = {};
        ArgumentsParser parser = new ArgumentsParser();

        // act
        Optional<String> value = parser.optionalArgument("key", args);

        // assert
        assertFalse(value.isPresent());
    }

    @Test
    public void optionalArgument_valuePresent_returnsValue() {
        // arrange
        String[] args = {"t=test", "t2=test2", "key=value"};
        ArgumentsParser parser = new ArgumentsParser();

        // act
        Optional<String> value = parser.optionalArgument("key", args);

        // assert
        assertTrue(value.isPresent());
        assertThat(value.get(), is("value"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void mandatoryArgument_valueNotPresent_throwsException() {
        // arrange
        String[] args = {};
        ArgumentsParser parser = new ArgumentsParser();

        // act
        parser.mandatoryArgument("key", args);
    }

    @Test
    public void mandatoryArgument_valuePresent_returnsValue() {
        // arrange
        String[] args = {"t=test", "t2=test2", "key=value"};
        ArgumentsParser parser = new ArgumentsParser();

        // act
        String value = parser.mandatoryArgument("key", args);

        // assert
        assertThat(value, is("value"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void optionalJsonArgument_valueIsNotParseable_throwsException() {
        // arrange
        String[] args = {"t=test", "t2=test2", "key=value"};
        ArgumentsParser parser = new ArgumentsParser();

        // act
        parser.optionalJsonArgument("key", args);
    }

    @Test
    public void optionalJsonArgument_valueNotPresent_returnsEmpty() {
        // arrange
        String[] args = {};
        ArgumentsParser parser = new ArgumentsParser();

        // act
        Optional<Map<String, Object>> value = parser.optionalJsonArgument("key", args);

        // assert
        assertFalse(value.isPresent());
    }

    @Test
    public void optionalJsonArgument_valuePresent_returnsParsedValue() {
        // arrange
        String[] args = {"t1=1", "t2=2", "key={\"attr1\":\"val1\", \"attr2\":\"val2\"}"};
        ArgumentsParser parser = new ArgumentsParser();

        // act
        Optional<Map<String, Object>> value = parser.optionalJsonArgument("key", args);

        // assert
        assertTrue(value.isPresent());
        assertThat(value.get().size(), is(2));
        assertThat(value.get().get("attr1"), is("val1"));
        assertThat(value.get().get("attr2"), is("val2"));
    }

    @Test
    public void optionalBooleanArgument_valuePresent_returnsParsedValue() {
        String[] args = { "enableNoValue", "enableWithValue=true", "disableWithValue=false"};
        ArgumentsParser parser = new ArgumentsParser();

        assertTrue(parser.optionalBooleanArgument("enableNoValue", args).orElse(false));
        assertTrue(parser.optionalBooleanArgument("enableWithValue", args).orElse(false));
        assertFalse(parser.optionalBooleanArgument("disableWithValue", args).orElse(true));
    }

    @Test
    public void optionalBooleanArgument_valueNotPresent_returnsParsedValue() {
        String[] args = { "otherFlag" };
        ArgumentsParser parser = new ArgumentsParser();

        assertFalse(parser.optionalBooleanArgument("disableNoValue", args).orElse(false));
        assertTrue(parser.optionalBooleanArgument("disableNoValue", args).orElse(true));
    }
}