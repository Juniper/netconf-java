package net.juniper.netconf;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Category(Test.class)
public class NetconfExceptionTest {
    private static final String TEST_MESSAGE = "test message";

    private void throwNetconfException() throws NetconfException {
        throw new NetconfException(TEST_MESSAGE);
    }

    @Test
    public void GIVEN_newNetconfException_THEN_exceptionCreated() {
        assertThatThrownBy(this::throwNetconfException)
                .isInstanceOf(NetconfException.class)
                .hasMessage(TEST_MESSAGE);
    }
}
