package net.juniper.netconf;

import net.juniper.netconf.element.Datastore;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class DatastoreTest {
    @Test
    public void testDatastoreName() {
        assertThat(Datastore.OPERATIONAL.toString()).isEqualTo("operational");
        assertThat(Datastore.RUNNING.toString()).isEqualTo("running");
        assertThat(Datastore.CANDIDATE.toString()).isEqualTo("candidate");
        assertThat(Datastore.STARTUP.toString()).isEqualTo("startup");
        assertThat(Datastore.INTENDED.toString()).isEqualTo("intended");
    }
}
