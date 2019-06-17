package net.juniper.netconf;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Category(Test.class)
public class CommitExceptionTest {
    private static final String TEST_MESSAGE = "test message";

    private void throwCommitException() throws CommitException {
        throw new CommitException(TEST_MESSAGE);
    }

    @Test
    public void GIVEN_newCommitException_THEN_exceptionCreated() {
        assertThatThrownBy(this::throwCommitException)
                .isInstanceOf(CommitException.class)
                .hasMessage(TEST_MESSAGE);
    }
}
