package net.juniper.netconf;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LoadExceptionTest {

    private static final String TEST_MESSAGE = "test message";

    private void throwLoadException() throws LoadException {
        throw new LoadException(TEST_MESSAGE);
    }


    @Test
    public void GIVEN_newLoadException_THEN_exceptionCreated() {
        assertThatThrownBy(this::throwLoadException)
                .isInstanceOf(LoadException.class)
                .hasMessage(TEST_MESSAGE);
    }
}
