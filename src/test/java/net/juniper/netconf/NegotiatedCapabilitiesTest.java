package net.juniper.netconf;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NegotiatedCapabilitiesTest {

    private static final List<String> CLIENT_CAPABILITIES = List.of(
        NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
        NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1
    );

    @Test
    public void normalizesLegacyAndRfcCandidateUris() throws Exception {
        NegotiatedCapabilities legacyCapabilities = NegotiatedCapabilities.fromCapabilities(
            CLIENT_CAPABILITIES,
            List.of(
                NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
                NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0 + "#candidate"
            )
        );
        NegotiatedCapabilities modernCapabilities = NegotiatedCapabilities.fromCapabilities(
            CLIENT_CAPABILITIES,
            List.of(
                NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
                "urn:ietf:params:netconf:capability:candidate:1.0"
            )
        );

        assertThat(legacyCapabilities.supportsCandidate()).isTrue();
        assertThat(modernCapabilities.supportsCandidate()).isTrue();
    }

    @Test
    public void normalizesLegacyAndRfcValidateUris() throws Exception {
        NegotiatedCapabilities legacyCapabilities = NegotiatedCapabilities.fromCapabilities(
            CLIENT_CAPABILITIES,
            List.of(
                NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
                NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0 + "#validate"
            )
        );
        NegotiatedCapabilities modernCapabilities = NegotiatedCapabilities.fromCapabilities(
            CLIENT_CAPABILITIES,
            List.of(
                NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
                "urn:ietf:params:netconf:capability:validate:1.1"
            )
        );

        assertThat(legacyCapabilities.supportsValidate()).isTrue();
        assertThat(modernCapabilities.supportsValidate()).isTrue();
    }

    @Test
    public void normalizesLegacyAndRfcConfirmedCommitUris() throws Exception {
        NegotiatedCapabilities legacyCapabilities = NegotiatedCapabilities.fromCapabilities(
            CLIENT_CAPABILITIES,
            List.of(
                NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
                NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0 + "#confirmed-commit"
            )
        );
        NegotiatedCapabilities modernCapabilities = NegotiatedCapabilities.fromCapabilities(
            CLIENT_CAPABILITIES,
            List.of(
                NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
                "urn:ietf:params:netconf:capability:confirmed-commit:1.1"
            )
        );

        assertThat(legacyCapabilities.supportsConfirmedCommit()).isTrue();
        assertThat(legacyCapabilities.supportsConfirmedCommit11()).isFalse();
        assertThat(modernCapabilities.supportsConfirmedCommit()).isTrue();
        assertThat(modernCapabilities.supportsConfirmedCommit11()).isTrue();
    }

    @Test
    public void prefersBase11WhenBothPeersSupportIt() throws Exception {
        NegotiatedCapabilities capabilities = NegotiatedCapabilities.fromCapabilities(
            CLIENT_CAPABILITIES,
            List.of(
                NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
                NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1
            )
        );

        assertThat(capabilities.getBaseVersion())
            .isEqualTo(NegotiatedCapabilities.BaseVersion.NETCONF_1_1);
        assertThat(capabilities.usesChunkedFraming()).isTrue();
    }

    @Test
    public void fallsBackToBase10WhenBase11IsNotShared() throws Exception {
        NegotiatedCapabilities capabilities = NegotiatedCapabilities.fromCapabilities(
            List.of(NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0),
            List.of(
                NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0,
                NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1
            )
        );

        assertThat(capabilities.getBaseVersion())
            .isEqualTo(NegotiatedCapabilities.BaseVersion.NETCONF_1_0);
        assertThat(capabilities.usesChunkedFraming()).isFalse();
    }

    @Test
    public void rejectsNoCommonBaseCapability() {
        assertThatThrownBy(() -> NegotiatedCapabilities.fromCapabilities(
            List.of(NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1),
            List.of(NetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_0)
        ))
            .isInstanceOf(NetconfException.class)
            .hasMessageContaining("do not share a common NETCONF base capability");
    }
}
