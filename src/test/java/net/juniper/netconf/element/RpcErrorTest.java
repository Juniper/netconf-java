package net.juniper.netconf.element;

import net.juniper.netconf.element.RpcError.ErrorSeverity;
import net.juniper.netconf.element.RpcError.ErrorTag;
import net.juniper.netconf.element.RpcError.ErrorType;
import net.juniper.netconf.element.RpcError.RpcErrorInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@link RpcError} record and its helper enums / builder.
 */
class RpcErrorTest {

    @Test
    void builderCreatesEquivalentRecord() {
        RpcErrorInfo info = RpcErrorInfo.builder()
                .badAttribute("attr")
                .sessionId("101")
                .build();

        RpcError fromBuilder = RpcError.builder()
                .errorType(ErrorType.RPC)
                .errorTag(ErrorTag.INVALID_VALUE)
                .errorSeverity(ErrorSeverity.ERROR)
                .errorPath("/interfaces/interface[name='xe-0/0/0']")
                .errorMessage("invalid value")
                .errorMessageLanguage("en")
                .errorInfo(info)
                .build();

        RpcError direct = new RpcError(ErrorType.RPC,
                                       ErrorTag.INVALID_VALUE,
                                       ErrorSeverity.ERROR,
                                       "/interfaces/interface[name='xe-0/0/0']",
                                       "invalid value",
                                       "en",
                                       info);

        assertThat(fromBuilder).isEqualTo(direct);
        assertThat(fromBuilder.hashCode()).isEqualTo(direct.hashCode());
    }

    @Test
    void enumsRoundTripFromString() {
        assertThat(ErrorType.from("protocol")).isEqualTo(ErrorType.PROTOCOL);
        assertThat(ErrorTag.from("unknown-element")).isEqualTo(ErrorTag.UNKNOWN_ELEMENT);
        assertThat(ErrorSeverity.from("warning")).isEqualTo(ErrorSeverity.WARNING);

        // unknown returns null
        assertThat(ErrorTag.from("does-not-exist")).isNull();
    }

    @Test
    void toStringContainsKeyFields() {
        RpcError error = RpcError.builder()
                                 .errorType(ErrorType.TRANSPORT)
                                 .errorTag(ErrorTag.LOCK_DENIED)
                                 .errorSeverity(ErrorSeverity.ERROR)
                                 .errorMessage("lock denied")
                                 .build();

        String txt = error.toString();
        assertThat(txt).contains("TRANSPORT")
                       .contains("LOCK_DENIED")
                       .contains("lock denied");
    }
}
